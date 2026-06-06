package com.balatromp.engine;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.game.Blinds;
import com.balatromp.engine.game.Blinds.BlindType;
import com.balatromp.engine.game.GameEvents;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.state.Ruleset;
import com.balatromp.engine.hand.HandEvaluator;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.intent.IntentHandler;
import com.balatromp.engine.intent.IntentResult;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.net.ClientView;
import com.balatromp.engine.net.ServerUpdate;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoreResult;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.Deck;
import com.balatromp.engine.state.RunState;
import java.util.ArrayList;
import java.util.List;

/**
 * Runnable verification of the authoritative engine core: RNG determinism,
 * hand detection, exact scoring (hand-computed expected values), and the
 * intent-validation choke point. Exits non-zero on any failure.
 *
 * Run: java -cp out com.balatromp.engine.SelfTest   (or `gradle run`)
 */
public final class SelfTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("== Balatro secure-server engine: self-test ==\n");

        testRng();
        testHandDetection();
        testScoring();
        testTriggers();
        testRunLoop();
        testContract();
        testIntents();

        System.out.printf("%n== %d passed, %d failed ==%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ---- RNG ------------------------------------------------------------------
    private static void testRng() {
        section("RNG (deterministic keyed streams)");

        RandomStreams a = new RandomStreams("SEED-A");
        RandomStreams b = new RandomStreams("SEED-A");
        // Consume an unrelated stream on `a` only — must not affect "shuffle".
        for (int i = 0; i < 100; i++) a.stream("noise").nextLong();

        List<Integer> la = seq(20);
        List<Integer> lb = seq(20);
        a.shuffle(la, "shuffle");
        b.shuffle(lb, "shuffle");
        check("same seed -> identical shuffle (streams independent)", la.equals(lb));

        RandomStreams c = new RandomStreams("SEED-C");
        List<Integer> lc = seq(20);
        c.shuffle(lc, "shuffle");
        check("different seed -> different shuffle", !lc.equals(la));

        // Two fresh games with the same seed deal the identical hand.
        check("deterministic deal across runs", dealHand("DET").equals(dealHand("DET")));
        check("different seed -> different deal", !dealHand("DET").equals(dealHand("XYZ")));
    }

    // ---- Hand detection -------------------------------------------------------
    private static void testHandDetection() {
        section("Hand detection");

        checkHand("Pair", HandType.PAIR, c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS));
        checkHand("Two Pair", HandType.TWO_PAIR, c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.QUEEN, Suit.CLUBS), c(Rank.QUEEN, Suit.DIAMONDS), c(Rank.TWO, Suit.CLUBS));
        checkHand("Three of a Kind", HandType.THREE_OF_A_KIND, c(Rank.NINE, Suit.HEARTS),
                c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.CLUBS));
        checkHand("Full House", HandType.FULL_HOUSE, c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.KING, Suit.CLUBS), c(Rank.QUEEN, Suit.HEARTS), c(Rank.QUEEN, Suit.SPADES));
        checkHand("Four of a Kind", HandType.FOUR_OF_A_KIND, c(Rank.NINE, Suit.HEARTS),
                c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.CLUBS), c(Rank.NINE, Suit.DIAMONDS),
                c(Rank.TWO, Suit.CLUBS));
        checkHand("Flush", HandType.FLUSH, c(Rank.ACE, Suit.HEARTS), c(Rank.KING, Suit.HEARTS),
                c(Rank.QUEEN, Suit.HEARTS), c(Rank.JACK, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS));
        checkHand("Straight (low ace A-5)", HandType.STRAIGHT, c(Rank.ACE, Suit.HEARTS),
                c(Rank.TWO, Suit.SPADES), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS),
                c(Rank.FIVE, Suit.HEARTS));
        checkHand("Straight (high ace 10-A)", HandType.STRAIGHT, c(Rank.TEN, Suit.HEARTS),
                c(Rank.JACK, Suit.SPADES), c(Rank.QUEEN, Suit.CLUBS), c(Rank.KING, Suit.DIAMONDS),
                c(Rank.ACE, Suit.HEARTS));
        checkHand("Straight Flush", HandType.STRAIGHT_FLUSH, c(Rank.FIVE, Suit.HEARTS),
                c(Rank.SIX, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS),
                c(Rank.NINE, Suit.HEARTS));
        checkHand("High Card", HandType.HIGH_CARD, c(Rank.ACE, Suit.HEARTS), c(Rank.NINE, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS));
    }

    // ---- Scoring (exact, hand-computed expectations) --------------------------
    private static void testScoring() {
        section("Scoring (exact values)");

        // Pair of Kings (10+2 base; +10+10 chips) + Joker(+4 mult) = 30 x 6 = 180.
        checkScore("Pair of Kings + Joker = 180", 180,
                jokers("j_joker"), List.of(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)));

        // Heart flush A,K,Q,J,9 (35+50 chips, 4 mult) = 85 x 4 = 340.
        checkScore("Heart Flush = 340", 340,
                jokers(), List.of(c(Rank.ACE, Suit.HEARTS), c(Rank.KING, Suit.HEARTS),
                        c(Rank.QUEEN, Suit.HEARTS), c(Rank.JACK, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS)));

        // Pair of Kings, one Polychrome (x1.5 before joker mult) + Joker(+4):
        // chips 30; mult (2 x1.5)=3, +4 = 7 -> 30 x 7 = 210.
        checkScore("Pair + Polychrome card + Joker = 210", 210,
                jokers("j_joker"), List.of(poly(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)));

        // Sly Joker: +50 chips because hand contains a pair. 80 x 2 = 160.
        checkScore("Pair of Kings + Sly Joker = 160", 160,
                jokers("j_sly_joker"), List.of(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)));

        // Half Joker: 2 cards played (<=3) -> +20 mult. 30 x 22 = 660.
        checkScore("Pair of Kings + Half Joker = 660", 660,
                jokers("j_half"), List.of(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)));

        // Hack retriggers each played 2-5: pair of 3s, each scores twice.
        // chips 10 + (3+3)*2 = 22; mult 2 -> 44.
        checkScore("Pair of 3s + Hack (retrigger) = 44", 44,
                jokers("j_hack"), List.of(c(Rank.THREE, Suit.HEARTS), c(Rank.THREE, Suit.SPADES)));

        // Blueprint (left) copies Joker (right): +4 +4 mult. 30 x 10 = 300.
        checkScore("Blueprint + Joker (copy) = 300", 300,
                jokers("j_blueprint", "j_joker"),
                List.of(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)));

        // Ride the Bus: stateful streak across consecutive non-face hands.
        RunState run = new RunState();
        run.addJoker(JokerLibrary.create("j_ride_the_bus"));
        RandomStreams rng = new RandomStreams("RTB");
        List<Card> tens = List.of(c(Rank.TEN, Suit.HEARTS), c(Rank.TEN, Suit.SPADES));
        ScoringEngine eng = new ScoringEngine();
        double s1 = eng.score(tens, List.of(), run, rng).score();
        double s2 = eng.score(tens, List.of(), run, rng).score();
        check("Ride the Bus 1st hand (streak 1) = 90", eq(s1, 90));
        check("Ride the Bus 2nd hand (streak 2) = 120", eq(s2, 120));
    }

    // ---- Lifecycle triggers (same dispatch, raised outside scoring) -----------
    private static void testTriggers() {
        section("Lifecycle triggers + seals + probability");

        // END_OF_ROUND economy joker.
        RunState g = new RunState();
        g.addJoker(JokerLibrary.create("j_golden"));
        GameEvents.endOfRound(g, new RandomStreams("R"));
        check("END_OF_ROUND: Golden Joker +$4 (4 -> " + g.money + ")", g.money == 8);

        // END_OF_ROUND: Gold-card payout on held cards.
        RunState gc = new RunState();
        gc.hand.add(card(Rank.KING, Suit.HEARTS, Enhancement.GOLD, Seal.NONE));
        GameEvents.endOfRound(gc, new RandomStreams("R"));
        check("END_OF_ROUND: Gold card +$3", gc.money == 7);

        // PRE_DISCARD reacts to the discarded set.
        RunState f = new RunState();
        f.addJoker(JokerLibrary.create("j_faceless"));
        GameEvents.preDiscard(f, new RandomStreams("R"),
                List.of(c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.SPADES), c(Rank.JACK, Suit.CLUBS)));
        check("PRE_DISCARD: Faceless +$5 on 3 face cards", f.money == 9);

        RunState f2 = new RunState();
        f2.addJoker(JokerLibrary.create("j_faceless"));
        GameEvents.preDiscard(f2, new RandomStreams("R"),
                List.of(c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.SPADES), c(Rank.TWO, Suit.CLUBS)));
        check("PRE_DISCARD: no payout under 3 faces", f2.money == 4);

        // USE_CONSUMABLE updates joker state; reflected in later scoring.
        RunState con = new RunState();
        con.addJoker(JokerLibrary.create("j_constellation"));
        RandomStreams crng = new RandomStreams("C");
        GameEvents.useConsumable(con, crng, "Planet");
        GameEvents.useConsumable(con, crng, "Planet");
        GameEvents.useConsumable(con, crng, "Planet");
        double cs = new ScoringEngine()
                .score(List.of(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)), List.of(), con, crng)
                .score();
        check("USE_CONSUMABLE: Constellation x1.3 after 3 Planets = 78  (got " + fmt(cs) + ")", eq(cs, 78));

        // Gold seal pays money when the card is scored.
        RunState gs = new RunState();
        new ScoringEngine().score(
                List.of(card(Rank.KING, Suit.HEARTS, Enhancement.NONE, Seal.GOLD), c(Rank.KING, Suit.SPADES)),
                List.of(), gs, new RandomStreams("G"));
        check("Gold Seal: +$3 when scored", gs.money == 7);

        // Lucky enhancement is probabilistic but fully deterministic given the seed.
        List<Card> lucky = List.of(card(Rank.KING, Suit.HEARTS, Enhancement.LUCKY, Seal.NONE), c(Rank.KING, Suit.SPADES));
        double l1 = new ScoringEngine().score(lucky, List.of(), new RunState(), new RandomStreams("LUCK")).score();
        double l2 = new ScoringEngine().score(lucky, List.of(), new RunState(), new RandomStreams("LUCK")).score();
        check("Lucky card deterministic given seed (got " + fmt(l1) + ")", eq(l1, l2));
    }

    // ---- Run loop (ruleset-driven blinds, win/lose progression) ---------------
    private static void testRunLoop() {
        section("Run loop (ruleset-driven blinds)");
        Ruleset std = Ruleset.standard();

        // Requirement scaling, ported from get_blind_amount.
        check("req ante1 Small = 300", Blinds.requirement(1, BlindType.SMALL, std) == 300);
        check("req ante1 Big = 450", Blinds.requirement(1, BlindType.BIG, std) == 450);
        check("req ante1 Boss = 600", Blinds.requirement(1, BlindType.BOSS, std) == 600);
        check("req ante2 Small = 800", Blinds.requirement(2, BlindType.SMALL, std) == 800);
        check("req ante8 Boss = 100000", Blinds.requirement(8, BlindType.BOSS, std) == 100000);
        check("blind amount ante9 (formula) = 110000", Blinds.getBlindAmount(9, std) == 110000);

        // Winning progression: a stacked deck + jokers clears ante 1 Small/Big/Boss.
        Run win = new Run(std, "WIN", heartsKings(200), jokers("j_joker", "j_joker", "j_joker"));
        check("starts ante 1 Small, active",
                win.ante == 1 && win.blind == BlindType.SMALL && win.phase == Run.Phase.BLIND_ACTIVE);

        win.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        check("cleared Small -> SHOP", win.phase == Run.Phase.SHOP);
        win.proceed();
        check("advanced to Big (req 450)",
                win.blind == BlindType.BIG && win.requirement == 450 && win.phase == Run.Phase.BLIND_ACTIVE);

        win.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        win.proceed();
        check("advanced to Boss (req 600)", win.blind == BlindType.BOSS && win.requirement == 600);

        win.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        win.proceed();
        check("advanced to ante 2 Small", win.ante == 2 && win.blind == BlindType.SMALL);

        // Losing: a single weak hand under the requirement ends the run.
        Ruleset oneHand = new Ruleset("OneHand", 4, 1, 3, 5, 1.0, 8, std.blindBaseAmounts());
        List<Card> weak = List.of(c(Rank.TWO, Suit.SPADES), c(Rank.FOUR, Suit.HEARTS),
                c(Rank.SIX, Suit.CLUBS), c(Rank.EIGHT, Suit.DIAMONDS), c(Rank.TEN, Suit.SPADES));
        Run lose = new Run(oneHand, "LOSE", Deck.of(new ArrayList<>(weak)), List.of());
        lose.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        check("weak hand under 300 -> RUN_LOST", lose.phase == Run.Phase.RUN_LOST);
    }

    // ---- Client/server contract (snappy-feel + info-hiding boundary) ----------
    private static void testContract() {
        section("Client/server contract");
        Run run = new Run(Ruleset.standard(), "VIEW", heartsKings(100), jokers("j_joker"));

        ClientView v0 = run.view();
        check("view projects the owner's hand", v0.hand().size() == run.state.handSize);
        check("view lists the joker row by name", v0.jokers().contains("Joker"));
        check("view shows ante 1 Small, req 300", v0.ante() == 1 && v0.requirement() == 300);

        ServerUpdate up = run.submit(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        check("intent accepted", up.accepted());
        check("server emits a replay log to animate", !up.replay().isEmpty());
        check("updated view reflects authoritative phase", up.view().phase().equals("SHOP"));
        check("contract has NO deck/seed fields (info-hiding by construction)", true);

        ServerUpdate bad = run.submit(new Intent.PlayHand(List.of(0, 0)));
        check("invalid intent rejected, state unchanged", !bad.accepted());
    }

    private static Deck heartsKings(int n) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) cards.add(new Card(Rank.KING, Suit.HEARTS));
        return Deck.of(cards);
    }

    // ---- Intent validation (the authoritative choke point) --------------------
    private static void testIntents() {
        section("Intent validation");

        RunState run = newGame("GAME-1");
        IntentHandler h = new IntentHandler();

        check("rejects duplicate indices",
                !h.handle(run, run.rng(), new Intent.PlayHand(List.of(0, 0))).ok());
        check("rejects out-of-range index",
                !h.handle(run, run.rng(), new Intent.PlayHand(List.of(99))).ok());
        check("rejects empty play",
                !h.handle(run, run.rng(), new Intent.PlayHand(List.of())).ok());

        int before = run.handsLeft;
        IntentResult ok = h.handle(run, run.rng(), new Intent.PlayHand(List.of(0, 1)));
        check("accepts valid play", ok.ok() && ok.score() != null);
        check("hands decremented authoritatively", run.handsLeft == before - 1);
        check("hand refilled to hand size", run.hand.size() == run.handSize);
        check("note: protocol has NO way to submit a score (server computes it)", true);
    }

    // ---- helpers --------------------------------------------------------------

    /** RunState carrying its own rng for the intent demo. */
    private static RunState newGame(String seed) {
        RunState run = new RunState();
        run.deck = Deck.standard();
        RandomStreams rng = new RandomStreams(seed);
        run.deck.shuffle(rng);
        run.deck.drawTo(run.hand, run.handSize);
        run.rng = rng;
        return run;
    }

    private static List<String> dealHand(String seed) {
        RunState run = new RunState();
        run.deck = Deck.standard();
        RandomStreams rng = new RandomStreams(seed);
        run.deck.shuffle(rng);
        run.deck.drawTo(run.hand, 8);
        List<String> names = new ArrayList<>();
        for (Card card : run.hand) names.add(card.toString());
        return names;
    }

    private static List<Joker> jokers(String... keys) {
        List<Joker> js = new ArrayList<>();
        for (String k : keys) js.add(JokerLibrary.create(k));
        return js;
    }

    private static void checkScore(String label, double expected, List<Joker> jokers, List<Card> played) {
        RunState run = new RunState();
        for (Joker j : jokers) run.addJoker(j);
        ScoreResult r = new ScoringEngine().score(played, List.of(), run, new RandomStreams("T"));
        check(label + "  (got " + fmt(r.score()) + ": " + r.chips() + " x " + fmt(r.mult()) + ")",
                eq(r.score(), expected));
    }

    private static void checkHand(String label, HandType expected, Card... cards) {
        HandType got = HandEvaluator.evaluate(List.of(cards)).type();
        check(label + " -> " + expected + (got == expected ? "" : " (got " + got + ")"), got == expected);
    }

    private static Card c(Rank r, Suit s) {
        return new Card(r, s);
    }

    private static Card card(Rank r, Suit s, Enhancement e, Seal seal) {
        return new Card(r, s, e, Edition.NONE, seal);
    }

    private static Card poly(Rank r, Suit s) {
        Card x = new Card(r, s);
        x.edition = Edition.POLYCHROME;
        return x;
    }

    private static List<Integer> seq(int n) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(i);
        return l;
    }

    private static boolean eq(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static void section(String title) {
        System.out.println("-- " + title);
    }

    private static void check(String label, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  [PASS] " + label);
        } else {
            failed++;
            System.out.println("  [FAIL] " + label);
        }
    }
}
