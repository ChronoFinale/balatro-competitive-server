package com.balatromp.engine.game;

import com.balatromp.engine.net.ClientView;
import com.balatromp.engine.state.Ruleset;
import com.balatromp.engine.state.RulesetStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A two-player competitive match — a same-seed race with full deckbuilding.
 *
 * <p>Each player drives their own full {@link Run} (blinds, boss blinds, shop,
 * jokers, planets, leveling) seeded identically, so the content available is the
 * same and the only variable is how each player builds and plays. The match is a
 * thin coordinator: it routes a player's actions to their Run, pushes the
 * opponent a live summary, and decides the match the moment a Run busts
 * (RUN_LOST → opponent wins) or wins its ante target (RUN_WON → that player wins).
 *
 * Transport-agnostic via an injected {@code (sessionId, payload)} sink. No client
 * ever sends a score; each player's Run is server-authoritative.
 *
 * Iteration: per-blind PvP/attrition with lives, sent Trap cards, ranked queue.
 */
public final class Match {

    public enum Phase { WAITING, AGREEING, PLAYING, FINISHED }

    private final String code;
    private final String seed;
    private final RulesetStore rulesets;
    private final BiConsumer<String, Object> transport;

    private Ruleset ruleset;
    private Ruleset proposed;       // host's current proposal, awaiting the guest's response
    private String proposedName;

    private static final int STARTING_LIVES = 4;     // Attrition default
    private static final int PVP_FROM_ANTE = 2;      // bosses from ante 2 are Nemesis blinds

    private Side host;
    private Side guest;
    private Phase phase = Phase.WAITING;

    public Match(String code, String seed, Ruleset fallback, RulesetStore rulesets,
                 BiConsumer<String, Object> transport) {
        this.code = code;
        this.seed = seed;
        this.ruleset = fallback; // until the players agree on one
        this.rulesets = rulesets;
        this.transport = transport;
    }

    public Phase phase() {
        return phase;
    }

    public void setHost(String sessionId, String playerId) {
        this.host = new Side(sessionId, playerId);
    }

    /** Adding the guest opens ruleset agreement: the host proposes, the guest accepts. */
    public synchronized void setGuestAndStart(String sessionId, String playerId) {
        this.guest = new Side(sessionId, playerId);
        startAgreement();
    }

    /** The player's authoritative Run (GameServer routes actions to it). */
    public Run runOf(String sessionId) {
        Side s = sideFor(sessionId);
        return s == null ? null : s.run;
    }

    /**
     * Called by GameServer after it applies an action to a player's Run: push the
     * opponent a live summary and decide the match if this Run ended.
     */
    public synchronized void onAction(String sessionId) {
        if (phase != Phase.PLAYING) return;
        Side me = sideFor(sessionId);
        if (me == null) return;
        Side opp = (me == host) ? guest : host;

        send(opp, opponentSummary(me));

        if (me.run.phase == Run.Phase.RUN_LOST) { // solo safety; shouldn't happen in Attrition
            finish(opp.playerId);
            return;
        }
        if (me.run.phase == Run.Phase.BLIND_FAILED) {
            me.lives--; // dying to any blind costs a life
            send(me, map("type", "lifeLost", "reason", "failed blind", "yourLives", me.lives));
            if (me.lives <= 0) {
                finish(opp.playerId);
                return;
            }
            me.run.continueAfterFail();
            pushView(me);
            send(opp, opponentSummary(me));
            return;
        }
        if (host.run.inPvpBlind() && guest.run.inPvpBlind()) {
            resolveNemesisIfDecided();
        }
    }

    /**
     * Attrition Nemesis resolution: it's a race. A player out of hands (locked)
     * who is BEHIND loses a life immediately. If both are out of hands, the lower
     * score loses (tie costs nothing). The player who's ahead needn't finish.
     */
    private void resolveNemesisIfDecided() {
        boolean hostLocked = host.run.phase == Run.Phase.PVP_PENDING;
        boolean guestLocked = guest.run.phase == Run.Phase.PVP_PENDING;
        if (!hostLocked && !guestLocked) return;

        long h = host.run.state.roundScore;
        long g = guest.run.state.roundScore;
        Side loser;
        if (hostLocked && guestLocked) {
            loser = h > g ? guest : g > h ? host : null; // both done; tie -> nobody
        } else if (hostLocked) {        // guest still has hands
            if (h >= g) return;          // host not behind yet; let guest finish
            loser = host;
        } else {                         // guest locked, host still has hands
            if (g >= h) return;
            loser = guest;
        }

        if (loser != null) loser.lives--;
        announce(host, guest, h, g, loser);
        announce(guest, host, g, h, loser);
        host.run.endPvp();   // both leave the Nemesis blind for their shop
        guest.run.endPvp();
        pushView(host);
        pushView(guest);

        if (host.lives <= 0) finish(guest.playerId);
        else if (guest.lives <= 0) finish(host.playerId);
    }

    private void announce(Side me, Side opp, long myScore, long oppScore, Side loser) {
        String outcome = loser == null ? "tie" : (loser == me ? "lose" : "win");
        send(me, map("type", "pvpResult", "ante", me.run.ante,
                "yourScore", myScore, "oppScore", oppScore, "outcome", outcome,
                "yourLives", me.lives, "oppLives", opp.lives));
    }

    private void pushView(Side s) {
        send(s, map("type", "update", "accepted", true, "view", s.run.view()));
    }

    // ---- ruleset agreement (host proposes, guest accepts) ----

    /** Both players see the full ruleset menu (curated + custom); the host proposes. */
    private void startAgreement() {
        phase = Phase.AGREEING;
        send(host, map("type", "lobbyReady", "youPropose", true, "rulesets", rulesets.all()));
        send(guest, map("type", "lobbyReady", "youPropose", false, "rulesets", rulesets.all()));
    }

    /** Host offers a ruleset by name; the guest sees its full data (params + joker pool). */
    public synchronized void propose(String sessionId, String name) {
        Side me = sideFor(sessionId);
        if (me == null) return;
        if (phase != Phase.AGREEING) {
            send(me, map("type", "error", "rejection", "not agreeing on a ruleset"));
            return;
        }
        if (me != host) {
            send(me, map("type", "error", "rejection", "only the host proposes the ruleset"));
            return;
        }
        Ruleset r = rulesets.get(name);
        if (r == null) {
            send(me, map("type", "error", "rejection", "unknown ruleset: " + name));
            return;
        }
        proposed = r;
        proposedName = name;
        send(guest, map("type", "rulesetProposed", "ruleset", r));
        send(host, map("type", "proposalSent", "name", name));
    }

    /** Guest accepts (start the match on the agreed ruleset) or declines (host re-proposes). */
    public synchronized void respond(String sessionId, boolean accept) {
        Side me = sideFor(sessionId);
        if (me == null) return;
        if (phase != Phase.AGREEING || me != guest) {
            send(me, map("type", "error", "rejection", "only the guest responds to a proposal"));
            return;
        }
        if (proposed == null) {
            send(me, map("type", "error", "rejection", "no ruleset has been proposed yet"));
            return;
        }
        if (accept) {
            ruleset = proposed;
            Map<String, Object> agreed = map("type", "rulesetAgreed", "name", proposedName);
            send(host, agreed);
            send(guest, agreed);
            startPlaying();
        } else {
            proposed = null;
            send(host, map("type", "rulesetDeclined", "name", proposedName));
            send(guest, map("type", "proposalDeclined"));
        }
    }

    // ---- play ----

    private void startPlaying() {
        phase = Phase.PLAYING;
        host.lives = STARTING_LIVES;
        guest.lives = STARTING_LIVES;
        host.run = new Run(ruleset, seed);   // same seed -> identical content available
        host.run.pvpFromAnte = PVP_FROM_ANTE;
        guest.run = new Run(ruleset, seed);
        guest.run.pvpFromAnte = PVP_FROM_ANTE;
        sendStart(host, guest);
        sendStart(guest, host);
    }

    private void sendStart(Side me, Side opp) {
        send(me, map("type", "matchStart", "opponent", opp.playerId,
                "yourLives", me.lives, "oppLives", opp.lives, "view", me.run.view()));
    }

    private void finish(String winner) {
        phase = Phase.FINISHED;
        Map<String, Object> end = map("type", "matchResult", "winner", winner);
        send(host, end);
        send(guest, end);
    }

    private Map<String, Object> opponentSummary(Side opp) {
        ClientView v = opp.run.view();
        return map("type", "opponentUpdate",
                "playerId", opp.playerId,
                "lives", opp.lives,
                "ante", v.ante(),
                "blind", v.blind(),
                "roundScore", v.roundScore(),
                "money", v.money(),
                "phase", v.phase());
    }

    private void send(Side s, Object payload) {
        transport.accept(s.sessionId, payload);
    }

    private Side sideFor(String sessionId) {
        if (host != null && host.sessionId.equals(sessionId)) return host;
        if (guest != null && guest.sessionId.equals(sessionId)) return guest;
        return null;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static final class Side {
        final String sessionId;
        final String playerId;
        Run run;
        int lives;

        Side(String sessionId, String playerId) {
            this.sessionId = sessionId;
            this.playerId = playerId;
        }
    }
}
