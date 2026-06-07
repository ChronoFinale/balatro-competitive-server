package com.balatromp.engine.game;

import com.balatromp.engine.net.ClientView;
import com.balatromp.engine.state.Ruleset;
import com.balatromp.engine.state.RulesetCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    public enum Phase { WAITING, DRAFTING, PLAYING, FINISHED }

    private final String code;
    private final String seed;
    private final BiConsumer<String, Object> transport;

    private Ruleset ruleset;
    private List<String> draftPool;
    private Side draftTurn;

    private static final int STARTING_LIVES = 4;     // Attrition default
    private static final int PVP_FROM_ANTE = 2;      // bosses from ante 2 are Nemesis blinds

    private Side host;
    private Side guest;
    private Phase phase = Phase.WAITING;

    public Match(String code, String seed, Ruleset ruleset, BiConsumer<String, Object> transport) {
        this.code = code;
        this.seed = seed;
        this.ruleset = ruleset; // fallback; replaced by the draft result
        this.transport = transport;
    }

    public Phase phase() {
        return phase;
    }

    public void setHost(String sessionId, String playerId) {
        this.host = new Side(sessionId, playerId);
    }

    /** Adding the guest opens the pick/ban draft. */
    public synchronized void setGuestAndStart(String sessionId, String playerId) {
        this.guest = new Side(sessionId, playerId);
        startDraft();
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

        if (me.run.phase == Run.Phase.RUN_LOST) {
            finish(opp.playerId); // busted a normal blind
            return;
        }
        // Both at a Nemesis blind with all hands played -> compare, lower loses a life.
        if (host.run.phase == Run.Phase.PVP_PENDING && guest.run.phase == Run.Phase.PVP_PENDING) {
            resolveNemesis();
        }
    }

    private void resolveNemesis() {
        long h = host.run.state.roundScore;
        long g = guest.run.state.roundScore;
        if (h > g) guest.lives--;
        else if (g > h) host.lives--;

        sendPvp(host, guest, h, g); // each player gets their own perspective
        sendPvp(guest, host, g, h);

        host.run.resolvePvp(); // both proceed to their shop
        guest.run.resolvePvp();

        if (host.lives <= 0) finish(guest.playerId);
        else if (guest.lives <= 0) finish(host.playerId);
    }

    private void sendPvp(Side me, Side opp, long myScore, long oppScore) {
        String outcome = myScore > oppScore ? "win" : oppScore > myScore ? "lose" : "tie";
        send(me, map("type", "pvpResult", "ante", me.run.ante,
                "yourScore", myScore, "oppScore", oppScore, "outcome", outcome,
                "yourLives", me.lives, "oppLives", opp.lives));
    }

    // ---- pick/ban draft ----

    private void startDraft() {
        phase = Phase.DRAFTING;
        draftPool = new ArrayList<>(RulesetCatalog.names());
        draftTurn = host;
        sendDraftState();
    }

    public synchronized void ban(String sessionId, String rulesetName) {
        Side me = sideFor(sessionId);
        if (me == null) return;
        if (phase != Phase.DRAFTING) {
            send(me, map("type", "error", "rejection", "not in draft"));
            return;
        }
        if (me != draftTurn) {
            send(me, map("type", "error", "rejection", "not your turn to ban"));
            return;
        }
        if (!draftPool.remove(rulesetName)) {
            send(me, map("type", "error", "rejection", "not in pool: " + rulesetName));
            return;
        }
        if (draftPool.size() == 1) {
            ruleset = RulesetCatalog.get(draftPool.get(0));
            Map<String, Object> result = map("type", "draftResult", "ruleset", ruleset.name());
            send(host, result);
            send(guest, result);
            startPlaying();
        } else {
            draftTurn = (me == host) ? guest : host;
            sendDraftState();
        }
    }

    private void sendDraftState() {
        send(host, map("type", "draftState", "pool", new ArrayList<>(draftPool), "yourTurn", draftTurn == host));
        send(guest, map("type", "draftState", "pool", new ArrayList<>(draftPool), "yourTurn", draftTurn == guest));
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
