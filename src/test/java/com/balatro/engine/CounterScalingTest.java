package com.balatro.engine;

import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Rank.TWO;
import static com.balatro.engine.card.Suit.HEARTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import com.balatro.engine.game.GameEvents;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.Trigger;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.state.RunState;
import org.junit.jupiter.api.Test;

/** Jokers that gain xMult per destroyed card (CARD_DESTROYED counter-scaling). */
class CounterScalingTest {

    private RunState run(String key) {
        RunState run = new RunState();
        run.rng = new RandomStreams("DESTROY");
        run.queues = new QueueSet(run.rng);
        run.addJoker(JokerLibrary.create(key));
        return run;
    }

    private static Card card(Enhancement e) {
        return new Card(KING, HEARTS, e, Edition.NONE, Seal.NONE);
    }

    @Test
    void glassJokerGainsPerGlassCardDestroyed() {
        RunState run = run("j_glass_joker");
        Joker gj = run.jokers().get(0);
        Card glass = card(Enhancement.GLASS);
        GameEvents.raise(Trigger.CARD_DESTROYED, run, run.rng, ctx -> ctx.scoredCard = glass);
        GameEvents.raise(Trigger.CARD_DESTROYED, run, run.rng, ctx -> ctx.scoredCard = glass);
        assertThat(((Number) run.jokerState(gj).get("x")).doubleValue()).isEqualTo(1.5); // 2 x 0.75

        // a non-Glass destruction does not count
        GameEvents.raise(Trigger.CARD_DESTROYED, run, run.rng, ctx -> ctx.scoredCard = card(Enhancement.NONE));
        assertThat(((Number) run.jokerState(gj).get("x")).doubleValue()).isEqualTo(1.5);
    }

    @Test
    void hitTheRoadGainsPerJackDiscarded() {
        RunState run = run("j_hit_the_road");
        Joker htr = run.jokers().get(0);
        GameEvents.raise(Trigger.BLIND_SELECTED, run, run.rng, null); // reset
        // discard a set with two Jacks and a non-Jack
        java.util.List<Card> discarded = java.util.List.of(
                new Card(com.balatro.engine.card.Rank.JACK, HEARTS, Enhancement.NONE, Edition.NONE, Seal.NONE),
                new Card(com.balatro.engine.card.Rank.JACK, com.balatro.engine.card.Suit.SPADES,
                        Enhancement.NONE, Edition.NONE, Seal.NONE),
                new Card(TWO, HEARTS, Enhancement.NONE, Edition.NONE, Seal.NONE));
        GameEvents.preDiscard(run, run.rng, discarded);
        assertThat(((Number) run.jokerState(htr).get("x")).doubleValue()).isEqualTo(1.0); // 2 Jacks x 0.5
    }

    @Test
    void yorickAccumulatesDiscardedCards() {
        RunState run = run("j_yorick");
        Joker yorick = run.jokers().get(0);
        // discard 3 cards, then 2 more -> total 5
        GameEvents.preDiscard(run, run.rng, java.util.List.of(card(Enhancement.NONE),
                card(Enhancement.NONE), card(Enhancement.NONE)));
        GameEvents.preDiscard(run, run.rng, java.util.List.of(card(Enhancement.NONE), card(Enhancement.NONE)));
        assertThat(((Number) run.jokerState(yorick).get("d")).intValue()).isEqualTo(5);
    }

    @Test
    void canioGainsPerFaceCardDestroyed() {
        RunState run = run("j_canio");
        Joker canio = run.jokers().get(0);
        GameEvents.raise(Trigger.CARD_DESTROYED, run, run.rng, ctx -> ctx.scoredCard = card(Enhancement.NONE));
        assertThat(((Number) run.jokerState(canio).get("x")).doubleValue()).isEqualTo(1.0); // King is a face

        // a non-face destruction does not count
        Card two = new Card(TWO, HEARTS, Enhancement.NONE, Edition.NONE, Seal.NONE);
        GameEvents.raise(Trigger.CARD_DESTROYED, run, run.rng, ctx -> ctx.scoredCard = two);
        assertThat(((Number) run.jokerState(canio).get("x")).doubleValue()).isEqualTo(1.0);
    }
}
