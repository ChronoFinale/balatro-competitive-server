package com.balatromp.engine;

import static com.balatromp.engine.card.Rank.KING;
import static com.balatromp.engine.card.Rank.TWO;
import static com.balatromp.engine.card.Suit.HEARTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.game.GameEvents;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.joker.Trigger;
import com.balatromp.engine.rng.QueueSet;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.state.RunState;
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
