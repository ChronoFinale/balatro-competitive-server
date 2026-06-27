package com.balatro.engine.joker.def;

import com.balatro.grammar.*;

import com.balatro.grammar.Hand;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.Contribution;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.JokerResult;
import com.balatro.grammar.Trigger;
import com.balatro.grammar.Effect.Term;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Per-scenario JSON round-trip fidelity for the data joker model: for representative jokers across each
 * relevant trigger (positive and negative cases), the live {@link DataJoker} must produce the exact same
 * {@link JokerResult} as one rebuilt from the serialize → deserialize of its def. If a joker survives a JSON
 * round-trip indistinguishably, the data model + its (de)serialization are faithful. (This was a
 * data-vs-hand-coded equivalence suite; every joker is data now, so round-trip fidelity is the invariant.)
 */
class DataJokerTest {

    private final ObjectMapper json = new ObjectMapper();

    // --- JokerResult accessors (the bag's old fields, read off the typed contributions) ---

    private static double sum(JokerResult r, Effect.Operation op, Term term) {
        double t = 0;
        for (Contribution c : r.contributions()) if (c.op() == op && c.term() == term) t += c.amount();
        return t;
    }
    private static double chips(JokerResult r) { return sum(r, Effect.Operation.ADD, Term.CHIPS); }
    private static double mult(JokerResult r) { return sum(r, Effect.Operation.ADD, Term.MULT); }
    private static double dollars(JokerResult r) { return sum(r, Effect.Operation.ADD, Term.DOLLARS); }
    private static Double xMult(JokerResult r) {
        for (Contribution c : r.contributions())
            if (c.op() == Effect.Operation.MULTIPLY && c.term() == Term.MULT) return c.amount();
        return null;
    }
    private static boolean isEmpty(JokerResult r) { return r.contributions().isEmpty() && r.commands().isEmpty(); }

    // --- flat / conditional / per-card jokers --------------------------------

    @Test
    void plainJoker() {
        assertEquivalent("j_joker", c -> c.phase = Trigger.JOKER_MAIN);
    }

    @Test
    void greedyJokerOnlyForDiamonds() {
        assertEquivalent("j_greedy_joker", c -> {
            c.phase = Trigger.ON_SCORED;
            c.scoredCard = c(Rank.SEVEN, Suit.DIAMONDS);
        });
        assertEquivalent("j_greedy_joker", c -> {
            c.phase = Trigger.ON_SCORED;
            c.scoredCard = c(Rank.SEVEN, Suit.CLUBS); // not a diamond -> empty
        });
    }

    @Test
    void slyJokerNeedsAPair() {
        assertEquivalent("j_sly_joker", c -> {
            c.phase = Trigger.JOKER_MAIN;
            c.handType = HandType.PAIR;
        });
        assertEquivalent("j_sly_joker", c -> {
            c.phase = Trigger.JOKER_MAIN;
            c.handType = HandType.HIGH_CARD; // no pair -> empty
        });
    }

    @Test
    void halfJokerOnSmallHands() {
        assertEquivalent("j_half", c -> {
            c.phase = Trigger.JOKER_MAIN;
            c.playedCards = List.of(c(Rank.TWO, Suit.SPADES), c(Rank.THREE, Suit.SPADES));
        });
        assertEquivalent("j_half", c -> {
            c.phase = Trigger.JOKER_MAIN;
            c.playedCards = List.of(c(Rank.TWO, Suit.SPADES), c(Rank.THREE, Suit.SPADES),
                    c(Rank.FOUR, Suit.SPADES), c(Rank.FIVE, Suit.SPADES), c(Rank.SIX, Suit.SPADES));
        });
    }

    @Test
    void evenStevenOnlyForEvenRanks() {
        assertEquivalent("j_even_steven", c -> {
            c.phase = Trigger.ON_SCORED;
            c.scoredCard = c(Rank.FOUR, Suit.SPADES); // even
        });
        assertEquivalent("j_even_steven", c -> {
            c.phase = Trigger.ON_SCORED;
            c.scoredCard = c(Rank.FIVE, Suit.SPADES); // odd -> empty
        });
    }

    @Test
    void hackRetriggersLowCards() {
        assertEquivalent("j_hack", c -> {
            c.phase = Trigger.REPETITION_PLAYED;
            c.scoredCard = c(Rank.THREE, Suit.SPADES); // 2-5 -> retrigger
        });
        assertEquivalent("j_hack", c -> {
            c.phase = Trigger.REPETITION_PLAYED;
            c.scoredCard = c(Rank.SIX, Suit.SPADES); // out of range -> empty
        });
    }

    @Test
    void goldenJokerPaysAtEndOfRound() {
        assertEquivalent("j_golden", c -> c.phase = Trigger.END_OF_ROUND);
        assertEquivalent("j_golden", c -> c.phase = Trigger.JOKER_MAIN); // wrong trigger -> empty
    }

    @Test
    void facelessJokerNeedsThreeDiscardedFaces() {
        assertEquivalent("j_faceless", c -> {
            c.phase = Trigger.PRE_DISCARD;
            c.eventCards = List.of(c(Rank.KING, Suit.SPADES), c(Rank.QUEEN, Suit.SPADES),
                    c(Rank.JACK, Suit.SPADES));
        });
        assertEquivalent("j_faceless", c -> {
            c.phase = Trigger.PRE_DISCARD;
            c.eventCards = List.of(c(Rank.KING, Suit.SPADES), c(Rank.QUEEN, Suit.SPADES)); // only 2
        });
    }

    // --- stateful jokers: drive a sequence, compare at every step ------------

    @Test
    void rideTheBusStreakMatches() {
        Joker code = JokerLibrary.create("j_ride_the_bus");
        Joker data = roundTrip("j_ride_the_bus");
        RunState run = new RunState();

        List<Card> noFace = List.of(c(Rank.TWO, Suit.SPADES), c(Rank.FIVE, Suit.HEARTS));
        List<Card> withFace = List.of(c(Rank.KING, Suit.SPADES));

        for (List<Card> scoring : List.of(noFace, noFace, withFace, noFace)) {
            step(code, data, run, c -> {
                c.phase = Trigger.BEFORE;
                c.scoringCards = scoring;
            });
            // JOKER_MAIN reads the streak the BEFORE step just advanced
            JokerResult e = step(code, data, run, c -> c.phase = Trigger.JOKER_MAIN);
            // sanity: a no-face streak yields positive mult, a broken streak yields nothing
            if (scoring == withFace) {
                assertThat(isEmpty(e)).isTrue();
            }
        }
    }

    @Test
    void constellationScalesPerPlanet() {
        Joker code = JokerLibrary.create("j_constellation");
        Joker data = roundTrip("j_constellation");
        RunState run = new RunState();

        // before any planet: no effect
        assertThat(isEmpty(step(code, data, run, c -> c.phase = Trigger.JOKER_MAIN))).isTrue();

        usePlanet(code, data, run);
        JokerResult one = step(code, data, run, c -> c.phase = Trigger.JOKER_MAIN);
        assertThat(isEmpty(one)).isFalse();
        assertThat(xMult(one)).isEqualTo(1.1);

        usePlanet(code, data, run);
        assertThat(xMult(step(code, data, run, c -> c.phase = Trigger.JOKER_MAIN))).isEqualTo(1.2);

        // a non-planet consumable must not advance the counter
        step(code, data, run, c -> {
            c.phase = Trigger.USE_CONSUMABLE;
            c.consumableType = com.balatro.grammar.ConsumableKind.TAROT;
        });
        assertThat(xMult(step(code, data, run, c -> c.phase = Trigger.JOKER_MAIN))).isEqualTo(1.2);
    }

    // --- the builder's serialization path -----------------------------------

    @Test
    void jokerDefsSurviveJsonRoundTrip() throws Exception {
        for (JokerDef def : JokerDefLibrary.all().values()) {
            String wire = json.writeValueAsString(def);
            JokerDef back = json.readValue(wire, JokerDef.class);
            assertThat(back).isEqualTo(def); // records + sealed types round-trip exactly
        }
    }

    @Test
    void aDeserializedDefStillScores() throws Exception {
        String wire = json.writeValueAsString(JokerDefLibrary.get("j_greedy_joker"));
        DataJoker j = new DataJoker(json.readValue(wire, JokerDef.class));
        JokerResult e = j.calculate(fresh(j, c -> {
            c.phase = Trigger.ON_SCORED;
            c.scoredCard = c(Rank.NINE, Suit.DIAMONDS);
        }));
        assertThat(mult(e)).isEqualTo(3.0);
    }

    @Test
    void registerDefFlowsThroughTheNormalCreatePath() {
        JokerDef custom = new JokerDef("j_test_custom", "Tester", "+7 Mult", com.balatro.grammar.Rarity.COMMON,
                3, 0, 0, null, null, true,
                List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                        Effect.mult(new Value.Const(7)))));
        JokerLibrary.registerDef(custom);

        Joker made = JokerLibrary.create("j_test_custom");
        assertThat(made).isInstanceOf(DataJoker.class);
        assertThat(made.info().name()).isEqualTo("Tester");
        JokerResult e = made.calculate(fresh(made, c -> c.phase = Trigger.JOKER_MAIN));
        assertThat(mult(e)).isEqualTo(7.0);
    }

    // --- expanded surface: card-property + run-state conditions (unit) -------

    @Test
    void scoredEnhancementCondition() {
        DataJoker j = oneRule(Trigger.ON_SCORED, new Condition.ScoredEnhancement(Enhancement.BONUS), Term.CHIPS, 25);
        assertThat(chips(j.calculate(fresh(j, c -> {
            c.phase = Trigger.ON_SCORED;
            c.scoredCard = new Card(Rank.NINE, Suit.SPADES, Enhancement.BONUS, Edition.NONE, Seal.NONE);
        })))).isEqualTo(25.0);
        assertThat(isEmpty(j.calculate(fresh(j, c -> {
            c.phase = Trigger.ON_SCORED;
            c.scoredCard = c(Rank.NINE, Suit.SPADES); // plain -> empty
        })))).isTrue();
    }

    @Test
    void runStateConditions() {
        DataJoker rich = oneRule(Trigger.JOKER_MAIN, new Condition.Compare(Value.Var.MONEY, Condition.Cmp.GTE, 10), Term.MULT, 3);
        assertThat(mult(rich.calculate(fresh(rich, c -> { c.phase = Trigger.JOKER_MAIN; c.run.money = 12; }))))
                .isEqualTo(3.0);
        assertThat(isEmpty(rich.calculate(fresh(rich, c -> { c.phase = Trigger.JOKER_MAIN; c.run.money = 4; })))).isTrue();

        DataJoker lateAnte = oneRule(Trigger.JOKER_MAIN, new Condition.Compare(Value.Var.ANTE, Condition.Cmp.GTE, 3), Term.MULT, 8);
        assertThat(mult(lateAnte.calculate(fresh(lateAnte, c -> { c.phase = Trigger.JOKER_MAIN; c.run.ante = 5; }))))
                .isEqualTo(8.0);
        assertThat(isEmpty(lateAnte.calculate(fresh(lateAnte, c -> { c.phase = Trigger.JOKER_MAIN; c.run.ante = 1; })))).isTrue();

        DataJoker finisher = oneRule(Trigger.JOKER_MAIN, new Condition.Compare(Hand.PLAYS, Condition.Cmp.EQ, 0), Term.MULT, 4);
        assertThat(mult(finisher.calculate(fresh(finisher, c -> { c.phase = Trigger.JOKER_MAIN; c.run.handsLeft = 0; }))))
                .isEqualTo(4.0);
    }

    // --- expanded surface: scaling values, end-to-end through the pipeline ---

    @Test
    void countOverPlayedCardsScalesEffect() {
        // +10 Chips per played face card
        DataJoker j = new DataJoker(scalingDef("j_face_chips", Term.CHIPS,
                new Value.Count(Value.Source.PLAYED, new Condition.ScoredIsFace(), 0, 10)));
        List<Card> played = List.of(c(Rank.KING, Suit.SPADES), c(Rank.QUEEN, Suit.SPADES),
                c(Rank.JACK, Suit.SPADES), c(Rank.TEN, Suit.SPADES), c(Rank.NINE, Suit.SPADES));
        ScoreResult with = scoreWith(j, played, List.of());
        ScoreResult without = scoreWith(null, played, List.of());
        assertThat(with.chips() - without.chips()).isEqualTo(30); // K, Q, J
        assertThat(with.mult()).isEqualTo(without.mult());
    }

    @Test
    void runVarScalesWithMoney() {
        // +1 Mult per dollar; RunState starts with $4
        DataJoker j = new DataJoker(scalingDef("j_greed", Term.MULT,
                new Value.RunVar(Value.Var.MONEY, 0, 1)));
        List<Card> played = List.of(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.CLUBS));
        ScoreResult with = scoreWith(j, played, List.of());
        ScoreResult without = scoreWith(null, played, List.of());
        assertThat(with.mult() - without.mult()).isEqualTo(4.0);
    }

    @Test
    void heldMultScalesWithHeldFaceCards() {
        // +3 Mult per face card held in hand
        DataJoker j = new DataJoker(scalingDef("j_held_face", Term.HELD_MULT,
                new Value.Count(Value.Source.HELD, new Condition.ScoredIsFace(), 0, 3)));
        List<Card> played = List.of(c(Rank.TWO, Suit.SPADES), c(Rank.THREE, Suit.SPADES),
                c(Rank.FOUR, Suit.SPADES), c(Rank.FIVE, Suit.SPADES), c(Rank.SIX, Suit.SPADES));
        List<Card> held = List.of(c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS), c(Rank.TWO, Suit.CLUBS));
        ScoreResult with = scoreWith(j, played, held);
        ScoreResult without = scoreWith(null, played, held);
        assertThat(with.mult() - without.mult()).isEqualTo(6.0); // 2 held faces x 3
    }

    // --- helpers -------------------------------------------------------------

    private static Card c(Rank r, Suit s) {
        return new Card(r, s);
    }

    private static DataJoker oneRule(Trigger when, Condition cond, Term subject, double amount) {
        return new DataJoker(new JokerDef("j_unit", "Unit", "test", com.balatro.grammar.Rarity.COMMON, 1, 0, 0, null, null, true,
                List.of(new Rule(when, cond, new Effect.Score(Effect.Operation.ADD, subject, new Value.Const(amount))))));
    }

    private static JokerDef scalingDef(String key, Term subject, Value value) {
        return new JokerDef(key, key, "test", com.balatro.grammar.Rarity.COMMON, 1, 0, 0, null, null, true,
                List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                        new Effect.Score(Effect.Operation.ADD, subject, value))));
    }

    private static ScoreResult scoreWith(Joker j, List<Card> played, List<Card> held) {
        RunState run = new RunState();
        if (j != null) run.addJoker(j);
        return new ScoringEngine().score(played, held, run, new RandomStreams("T"));
    }

    /** A joker built from the JSON round-trip of its def — serialize → deserialize → interpret. */
    private Joker roundTrip(String key) {
        try {
            JokerDef def = JokerDefLibrary.get(key);
            return new DataJoker(json.readValue(json.writeValueAsString(def), JokerDef.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** The live def must behave identically to its JSON round-trip across this scenario — i.e. serialize →
     *  deserialize preserves the joker's result for the given trigger/condition. */
    private void assertEquivalent(String key, java.util.function.Consumer<EvaluationContext> setup) {
        try {
            Joker live = JokerLibrary.create(key);
            JokerDef def = JokerDefLibrary.get(key);
            Joker roundTripped = new DataJoker(json.readValue(json.writeValueAsString(def), JokerDef.class));
            JokerResult expected = live.calculate(fresh(live, setup));
            JokerResult actual = roundTripped.calculate(fresh(roundTripped, setup));
            assertSame(key, expected, actual);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Drive both jokers with the same context against a shared run; return the data joker's result. */
    private JokerResult step(Joker code, Joker data, RunState run,
            java.util.function.Consumer<EvaluationContext> setup) {
        JokerResult expected = code.calculate(ctx(code, run, setup));
        JokerResult actual = data.calculate(ctx(data, run, setup));
        assertSame("stateful", expected, actual);
        return actual;
    }

    private void usePlanet(Joker code, Joker data, RunState run) {
        step(code, data, run, c -> {
            c.phase = Trigger.USE_CONSUMABLE;
            c.consumableType = com.balatro.grammar.ConsumableKind.PLANET;
        });
    }

    private static EvaluationContext fresh(Joker self, java.util.function.Consumer<EvaluationContext> setup) {
        return ctx(self, new RunState(), setup);
    }

    private static EvaluationContext ctx(Joker self, RunState run,
            java.util.function.Consumer<EvaluationContext> setup) {
        EvaluationContext c = new EvaluationContext();
        c.jokers = List.of(self);
        c.selfIndex = 0;
        c.blueprintDepth = 0;
        c.run = run;
        setup.accept(c);
        return c;
    }

    /** Two results are equivalent when their contributions and commands match exactly (records → equals). */
    private void assertSame(String key, JokerResult expected, JokerResult actual) {
        assertThat(actual.contributions()).as("%s contributions", key).isEqualTo(expected.contributions());
        assertThat(actual.commands()).as("%s commands", key).isEqualTo(expected.commands());
    }
}
