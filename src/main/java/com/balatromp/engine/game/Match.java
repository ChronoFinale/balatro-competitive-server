package com.balatromp.engine.game;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.intent.IntentHandler;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.state.Deck;
import com.balatromp.engine.state.Ruleset;
import com.balatromp.engine.state.RulesetCatalog;
import com.balatromp.engine.state.RunState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A two-player competitive match. First competitive format ("Duel"):
 *
 * <ul>
 *   <li>Both players share the SAME seed, so each round deals identical cards —
 *       the only variable is how each player chooses to play them (pure skill).
 *   <li>Each round, both play their hand allotment; the server accumulates each
 *       player's authoritative score. When both are done, scores are compared:
 *       the lower score loses a life (ties cost nothing).
 *   <li>First to 0 lives loses the match.
 * </ul>
 *
 * Transport-agnostic: messages go out through a {@code (sessionId, payload)}
 * sink injected by the server, which is also where WebSocket server-push happens
 * (each player is pushed the opponent's live state). No client ever sends a
 * score; the server computes both.
 *
 * <p>Iteration slots left open: per-round requirement floor, jokers/shop between
 * rounds, pick/ban to choose the ruleset, ranked queue entry.
 */
public final class Match {

    public enum Phase { WAITING, DRAFTING, PLAYING, FINISHED }

    private static final int STARTING_LIVES = 3;

    private final String code;
    private final String seed;
    private final BiConsumer<String, Object> transport;
    private final IntentHandler intents = new IntentHandler();

    private Ruleset ruleset;                 // resolved by the pick/ban draft
    private List<String> draftPool;
    private Side draftTurn;

    private Side host;
    private Side guest;
    private int round = 0;
    private Phase phase = Phase.WAITING;

    public Match(String code, String seed, Ruleset ruleset, BiConsumer<String, Object> transport) {
        this.code = code;
        this.seed = seed;
        this.ruleset = ruleset; // fallback; replaced by the draft result
        this.transport = transport;
    }

    public String code() {
        return code;
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

    // ---- pick/ban draft ----

    private void startDraft() {
        phase = Phase.DRAFTING;
        draftPool = new ArrayList<>(RulesetCatalog.names());
        draftTurn = host; // host bans first
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

    private void startPlaying() {
        host.lives = STARTING_LIVES;
        guest.lives = STARTING_LIVES;
        phase = Phase.PLAYING;
        round = 1;
        beginRound();
        sendBoth("matchStart");
    }

    public synchronized void play(String sessionId, Intent intent) {
        if (phase != Phase.PLAYING) {
            send(sideFor(sessionId), map("type", "error", "rejection", "match not active"));
            return;
        }
        Side me = sideFor(sessionId);
        Side opp = (me == host) ? guest : host;
        if (me == null) return;
        if (me.done) {
            send(me, map("type", "error", "rejection", "you are done this round"));
            return;
        }

        var result = intents.handle(me.state, me.rng, intent);
        send(me, viewFor(me, opp, "update", result.ok(), result.error()));

        me.done = me.state.handsLeft <= 0;
        send(opp, opponentUpdate(me));

        if (host.done && guest.done) {
            resolveRound();
        }
    }

    // ---- round lifecycle ----

    private void beginRound() {
        beginSide(host);
        beginSide(guest);
    }

    private void beginSide(Side s) {
        s.state.roundScore = 0;
        s.state.handsLeft = ruleset.hands();
        s.state.discardsLeft = ruleset.discards();
        s.state.handSize = ruleset.handSize();
        s.state.hand.clear();
        s.state.deck = freshDeck(round);
        s.state.rng = new RandomStreams(seed); // same seed -> fair scoring RNG
        s.state.deck.drawTo(s.state.hand, s.state.handSize);
        s.done = false;
    }

    private void resolveRound() {
        long h = host.state.roundScore;
        long g = guest.state.roundScore;
        String winner = h > g ? host.playerId : g > h ? guest.playerId : "tie";
        if (h > g) guest.lives--;
        else if (g > h) host.lives--;

        Map<String, Object> result = map(
                "type", "roundResult",
                "round", round,
                "hostScore", h,
                "guestScore", g,
                "winner", winner,
                "hostLives", host.lives,
                "guestLives", guest.lives);
        send(host, result);
        send(guest, result);

        if (host.lives <= 0 || guest.lives <= 0) {
            phase = Phase.FINISHED;
            String matchWinner = host.lives > 0 ? host.playerId : guest.playerId;
            Map<String, Object> end = map("type", "matchResult", "winner", matchWinner);
            send(host, end);
            send(guest, end);
        } else {
            round++;
            beginRound();
            sendBoth("roundStart");
        }
    }

    // ---- messaging ----

    private void sendBoth(String type) {
        send(host, viewFor(host, guest, type, true, null));
        send(guest, viewFor(guest, host, type, true, null));
    }

    private Map<String, Object> viewFor(Side me, Side opp, String type, boolean accepted, String rejection) {
        return map(
                "type", type,
                "accepted", accepted,
                "rejection", rejection,
                "code", code,
                "round", round,
                "roundScore", me.state.roundScore,
                "handsLeft", me.state.handsLeft,
                "discardsLeft", me.state.discardsLeft,
                "yourLives", me.lives,
                "oppLives", opp.lives,
                "hand", handOf(me));
    }

    private Map<String, Object> opponentUpdate(Side opp) {
        return map(
                "type", "opponentUpdate",
                "score", opp.state.roundScore,
                "handsLeft", opp.state.handsLeft,
                "done", opp.done);
    }

    private void send(Side s, Object payload) {
        transport.accept(s.sessionId, payload);
    }

    private Side sideFor(String sessionId) {
        if (host != null && host.sessionId.equals(sessionId)) return host;
        if (guest != null && guest.sessionId.equals(sessionId)) return guest;
        return null;
    }

    // ---- helpers ----

    private List<Map<String, Object>> handOf(Side s) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Card c : s.state.hand) {
            cards.add(map("rank", c.rank.name(), "suit", c.suit.name(),
                    "enhancement", c.enhancement.name(), "edition", c.edition.name(),
                    "seal", c.seal.name()));
        }
        return cards;
    }

    private Deck freshDeck(int forRound) {
        List<Card> cards = new ArrayList<>();
        for (Suit s : Suit.values()) {
            for (Rank r : Rank.values()) {
                cards.add(new Card(r, s));
            }
        }
        new RandomStreams(seed + ":deal:" + forRound).shuffle(cards, "deal");
        return Deck.of(cards);
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static final class Side {
        final String sessionId;
        final String playerId;
        final RunState state = new RunState();
        RandomStreams rng;
        boolean done;
        int lives;

        Side(String sessionId, String playerId) {
            this.sessionId = sessionId;
            this.playerId = playerId;
        }
    }
}
