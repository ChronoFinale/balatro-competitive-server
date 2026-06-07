package com.balatromp.engine.joker.def;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.joker.EvaluationContext;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.JokerEffect;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.joker.Trigger;
import com.balatromp.engine.state.RunState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The data-driven joker model proves itself against the hand-coded set: for every
 * {@link JokerDefLibrary} entry, the interpreted {@link DataJoker} must produce
 * the exact same numeric effect as its hand-coded twin in {@code JokerLibrary},
 * across each relevant trigger (positive and negative). If a data joker and a code
 * joker can't be told apart by the scoring pipeline, the framework is faithful.
 */
class DataJokerTest {

    private final ObjectMapper json = new ObjectMapper();

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
            c.scoredCard = c(Rank.SEVEN, Suit.CLUBS); // not a diamond -> null
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
            c.handType = HandType.HIGH_CARD; // no pair -> null
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
            c.scoredCard = c(Rank.FIVE, Suit.SPADES); // odd -> null
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
            c.scoredCard = c(Rank.SIX, Suit.SPADES); // out of range -> null
        });
    }

    @Test
    void goldenJokerPaysAtEndOfRound() {
        assertEquivalent("j_golden", c -> c.phase = Trigger.END_OF_ROUND);
        assertEquivalent("j_golden", c -> c.phase = Trigger.JOKER_MAIN); // wrong trigger -> null
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
        Joker data = new DataJoker(JokerDefLibrary.get("j_ride_the_bus"));
        RunState run = new RunState();

        List<Card> noFace = List.of(c(Rank.TWO, Suit.SPADES), c(Rank.FIVE, Suit.HEARTS));
        List<Card> withFace = List.of(c(Rank.KING, Suit.SPADES));

        for (List<Card> scoring : List.of(noFace, noFace, withFace, noFace)) {
            step(code, data, run, c -> {
                c.phase = Trigger.BEFORE;
                c.scoringCards = scoring;
            });
            // JOKER_MAIN reads the streak the BEFORE step just advanced
            JokerEffect e = step(code, data, run, c -> c.phase = Trigger.JOKER_MAIN);
            // sanity: a no-face streak yields positive mult, a broken streak yields nothing
            if (scoring == withFace) {
                assertThat(e).isNull();
            }
        }
    }

    @Test
    void constellationScalesPerPlanet() {
        Joker code = JokerLibrary.create("j_constellation");
        Joker data = new DataJoker(JokerDefLibrary.get("j_constellation"));
        RunState run = new RunState();

        // before any planet: no effect
        assertThat(step(code, data, run, c -> c.phase = Trigger.JOKER_MAIN)).isNull();

        usePlanet(code, data, run);
        JokerEffect one = step(code, data, run, c -> c.phase = Trigger.JOKER_MAIN);
        assertThat(one).isNotNull();
        assertThat(one.xMult).isEqualTo(1.1);

        usePlanet(code, data, run);
        assertThat(step(code, data, run, c -> c.phase = Trigger.JOKER_MAIN).xMult).isEqualTo(1.2);

        // a non-planet consumable must not advance the counter
        step(code, data, run, c -> {
            c.phase = Trigger.USE_CONSUMABLE;
            c.consumableType = "Tarot";
        });
        assertThat(step(code, data, run, c -> c.phase = Trigger.JOKER_MAIN).xMult).isEqualTo(1.2);
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
        JokerEffect e = j.calculate(fresh(j, c -> {
            c.phase = Trigger.ON_SCORED;
            c.scoredCard = c(Rank.NINE, Suit.DIAMONDS);
        }));
        assertThat(e.mult).isEqualTo(3);
    }

    @Test
    void registerDefFlowsThroughTheNormalCreatePath() {
        JokerDef custom = new JokerDef("j_test_custom", "Tester", "+7 Mult", "Common",
                3, 0, 0, null, null, true, List.of(),
                List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                        new EffectTemplate(EffectTemplate.Op.MULT, new Value.Const(7)))));
        JokerLibrary.registerDef(custom);

        Joker made = JokerLibrary.create("j_test_custom");
        assertThat(made).isInstanceOf(DataJoker.class);
        assertThat(made.info().name()).isEqualTo("Tester");
        JokerEffect e = made.calculate(fresh(made, c -> c.phase = Trigger.JOKER_MAIN));
        assertThat(e.mult).isEqualTo(7);
    }

    // --- helpers -------------------------------------------------------------

    private static Card c(Rank r, Suit s) {
        return new Card(r, s);
    }

    /** Run the same (freshly built) context through the code and data jokers; assert identical output. */
    private void assertEquivalent(String key, java.util.function.Consumer<EvaluationContext> setup) {
        Joker code = JokerLibrary.create(key);
        Joker data = new DataJoker(JokerDefLibrary.get(key));
        JokerEffect expected = code.calculate(fresh(code, setup));
        JokerEffect actual = data.calculate(fresh(data, setup));
        assertSame(key, expected, actual);
    }

    /** Drive both jokers with the same context against a shared run; return the data joker's effect. */
    private JokerEffect step(Joker code, Joker data, RunState run,
            java.util.function.Consumer<EvaluationContext> setup) {
        JokerEffect expected = code.calculate(ctx(code, run, setup));
        JokerEffect actual = data.calculate(ctx(data, run, setup));
        assertSame("stateful", expected, actual);
        return actual;
    }

    private void usePlanet(Joker code, Joker data, RunState run) {
        step(code, data, run, c -> {
            c.phase = Trigger.USE_CONSUMABLE;
            c.consumableType = "Planet";
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

    private void assertSame(String key, JokerEffect expected, JokerEffect actual) {
        if (expected == null) {
            assertThat(actual).as("%s should produce no effect", key).isNull();
            return;
        }
        assertThat(actual).as("%s should produce an effect", key).isNotNull();
        assertThat(actual.chips).as("%s chips", key).isEqualTo(expected.chips);
        assertThat(actual.mult).as("%s mult", key).isEqualTo(expected.mult);
        assertThat(actual.xMult).as("%s xMult", key).isEqualTo(expected.xMult);
        assertThat(actual.dollars).as("%s dollars", key).isEqualTo(expected.dollars);
        assertThat(actual.repetitions).as("%s repetitions", key).isEqualTo(expected.repetitions);
    }
}
