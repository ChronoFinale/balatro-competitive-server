package com.balatro.engine.game;

import com.balatro.engine.net.ClientView;
import com.balatro.engine.state.Gamemode;
import com.balatro.engine.state.Ruleset;
import com.balatro.engine.state.RulesetStore;
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
    private String seed;            // re-rolled on a rematch (same pairing, fresh content)
    private final RulesetStore rulesets;
    private final BiConsumer<String, Object> transport;

    // Head-to-head tally for THIS pairing — accumulates across rematches on the same Match object
    // (one Match = one rivalry). In-memory only; durable W/L moves to Account in R3.
    private int hostWins;
    private int guestWins;
    // Players who've agreed to a rematch after a FINISHED match (keyed by stable playerId, not sessionId,
    // so it survives a reconnect). Both present -> start the rematch.
    private final java.util.Set<String> rematchAgreed = new java.util.HashSet<>();

    private Ruleset ruleset;
    private Ruleset proposed;       // host's current proposal, awaiting the guest's response
    private String proposedName;

    private Gamemode mode = Gamemode.ATTRITION; // the match's gamemode — its config (starting lives, PvP-from-ante)

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

    /** The match's gamemode (Attrition today). */
    public Gamemode mode() {
        return mode;
    }

    /** This pairing's two players (null until each side joins). */
    public String hostPlayerId() {
        return host == null ? null : host.playerId;
    }

    public String guestPlayerId() {
        return guest == null ? null : guest.playerId;
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

        syncNemesis(); // refresh each Run's opponent view so Nemesis jokers read live state

        // Asteroid (MP): a player who used it delevels the OPPONENT's highest-level hand.
        if (me.run.state.nemesisDelevelPending > 0) {
            for (int i = 0; i < me.run.state.nemesisDelevelPending; i++) delevelHighestHand(opp.run);
            me.run.state.nemesisDelevelPending = 0;
            pushView(opp); // the opponent's hand levels changed
        }

        // On entering a PvP blind, raise PVP_BLIND_REACHED so jokers react as data (Speedrun: create a
        // Spectral if you arrived before your Nemesis). The Match supplies "first" — it alone sees both runs.
        if (me.run.inPvpBlind() && !me.wasInPvp) {
            me.run.pvpReached(!opp.run.inPvpBlind());
        }
        me.wasInPvp = me.run.inPvpBlind();

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
        // Raise PVP_BLIND_ENDED on each run with the Nemesis as context, so jokers react as data
        // (Pizza: consume self, +1 discard to me, +2 to the Nemesis). Each run sees the other as opponentRun.
        host.run.pvpEnded(guest.run.state);
        guest.run.pvpEnded(host.run.state);
        syncNemesis(); // lives changed -> refresh Defensive Joker's "lives behind", etc.
        announce(host, guest, h, g, loser);
        announce(guest, host, g, h, loser);
        host.run.endPvp();   // both leave the Nemesis blind for their shop
        guest.run.endPvp();
        pushView(host);
        pushView(guest);

        if (host.lives <= 0) finish(guest.playerId);
        else if (guest.lives <= 0) finish(host.playerId);
    }

    /** Push each Run its opponent's live state, so the Nemesis jokers (Defensive, Conjoined,
     *  Taxes, Skip-Off, Pacifist) score off real opponent data instead of solo defaults. */
    private void syncNemesis() {
        if (host == null || guest == null || host.run == null || guest.run == null) return;
        applyOpponent(host, guest);
        applyOpponent(guest, host);
    }

    /** Delevel a run's highest-level poker hand; ties go to the higher-ranked hand
     *  (Flush Five → … → High Card), and a hand can't drop below level 1 (Asteroid). */
    private static void delevelHighestHand(Run run) {
        com.balatro.engine.hand.HandType[] hands = com.balatro.engine.hand.HandType.values();
        int max = 1;
        for (com.balatro.engine.hand.HandType h : hands) max = Math.max(max, run.state.handLevel(h));
        if (max <= 1) return; // nothing leveled above base
        for (int i = hands.length - 1; i >= 0; i--) { // from the best hand down
            if (run.state.handLevel(hands[i]) == max) {
                run.state.levelDownHand(hands[i]);
                return;
            }
        }
    }


    private static void applyOpponent(Side me, Side opp) {
        me.run.state.myLives = me.lives;
        me.run.state.opponent.lives = opp.lives;
        me.run.state.opponent.handsLeft = opp.run.state.handsLeft;
        me.run.state.opponent.cardsSold = opp.run.state.cardsSoldSinceLastPvp;
        me.run.state.opponent.blindsSkipped = opp.run.state.blindsSkipped;
        me.run.state.opponent.shopSpentLastAnte = opp.run.state.shopSpentLastAnte;
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
        if (!com.balatro.engine.state.BundleCatalog.isPvp(name)) {
            send(me, map("type", "error", "rejection", "ruleset is single-player only: " + name));
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
        host.lives = mode.startingLives();
        guest.lives = mode.startingLives();
        host.run = new Run(ruleset, seed);   // same seed -> identical content available
        host.run.pvpFromAnte = mode.pvpFromAnte();
        guest.run = new Run(ruleset, seed);
        guest.run.pvpFromAnte = mode.pvpFromAnte();
        syncNemesis(); // seed each Run's opponent view before the first hand
        sendStart(host, guest);
        sendStart(guest, host);
    }

    private void sendStart(Side me, Side opp) {
        send(me, map("type", "matchStart", "opponent", opp.playerId,
                "yourLives", me.lives, "oppLives", opp.lives, "view", me.run.view()));
    }

    private void finish(String winner) {
        phase = Phase.FINISHED;
        if (host != null && host.playerId.equals(winner)) hostWins++;
        else if (guest != null && guest.playerId.equals(winner)) guestWins++;
        rematchAgreed.clear(); // a fresh result -> fresh rematch agreement
        Map<String, Object> end = map("type", "matchResult", "winner", winner, "headToHead", headToHead());
        send(host, end);
        send(guest, end);
    }

    /** This pairing's running score, by playerId. */
    private Map<String, Object> headToHead() {
        Map<String, Object> h2h = new LinkedHashMap<>();
        if (host != null) h2h.put(host.playerId, hostWins);
        if (guest != null) h2h.put(guest.playerId, guestWins);
        return h2h;
    }

    // ---- rematch (same pairing, fresh seed, agreed ruleset) ----

    /**
     * One side asks to play again after a FINISHED match. Marks them agreed and notifies the opponent.
     * Returns true once BOTH have agreed — the caller then starts the rematch with a fresh seed. The
     * rematch reuses the already-agreed ruleset (no second agreement round).
     */
    public synchronized boolean requestRematch(String sessionId) {
        Side me = sideFor(sessionId);
        if (me == null) return false;
        if (phase != Phase.FINISHED) {
            send(me, map("type", "error", "rejection", "no finished match to rematch"));
            return false;
        }
        rematchAgreed.add(me.playerId);
        if (host != null && guest != null
                && rematchAgreed.contains(host.playerId) && rematchAgreed.contains(guest.playerId)) {
            return true;
        }
        Side opp = (me == host) ? guest : host;
        send(me, map("type", "rematchPending"));
        if (opp != null) send(opp, map("type", "rematchOffered", "from", me.playerId));
        return false;
    }

    /** Replay the same pairing on the agreed ruleset with a fresh seed. Caller supplies the seed (the
     *  server owns seed generation). No-op unless the match is FINISHED. */
    public synchronized void startRematch(String newSeed) {
        if (phase != Phase.FINISHED) return;
        this.seed = newSeed;
        rematchAgreed.clear();
        startPlaying(); // resets lives, builds fresh Runs on the agreed ruleset, pushes matchStart to both
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

    private Side sideForPlayer(String playerId) {
        if (host != null && host.playerId.equals(playerId)) return host;
        if (guest != null && guest.playerId.equals(playerId)) return guest;
        return null;
    }

    // ---- reconnect (the identity is the playerId; the socket may change) ----

    /** Point a player's Side at a new session (they reconnected on a fresh socket). */
    public synchronized boolean rebindSession(String playerId, String newSessionId) {
        Side s = sideForPlayer(playerId);
        if (s == null) return false;
        s.sessionId = newSessionId;
        return true;
    }

    /** Re-push a reconnected player their match start + current authoritative view, so the client
     *  re-renders exactly the live state. No-op unless the match is in progress. */
    public synchronized void resendStateTo(String playerId) {
        if (phase != Phase.PLAYING) return;
        Side me = sideForPlayer(playerId);
        if (me == null) return;
        sendStart(me, (me == host) ? guest : host);
    }

    /** A player abandoned the match (grace window elapsed without a reconnect): the opponent wins.
     *  No-op unless the match is in progress. */
    public synchronized void forfeit(String playerId) {
        if (phase != Phase.PLAYING) return;
        Side leaver = sideForPlayer(playerId);
        if (leaver == null) return;
        Side opp = (leaver == host) ? guest : host;
        if (opp != null) finish(opp.playerId);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static final class Side {
        String sessionId;       // rebound on reconnect (the identity is playerId, not the socket)
        final String playerId;
        Run run;
        int lives;
        boolean wasInPvp; // tracks the transition into a PvP blind (Speedrun fires once)

        Side(String sessionId, String playerId) {
            this.sessionId = sessionId;
            this.playerId = playerId;
        }
    }
}
