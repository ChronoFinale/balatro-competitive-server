package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.TestSupport.card;
import static com.balatro.engine.card.Rank.JACK;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Rank.QUEEN;
import static com.balatro.engine.card.Rank.TWO;
import static com.balatro.engine.card.Suit.CLUBS;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import com.balatro.engine.game.GameEvents;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

class LifecycleTest {

    @Test
    void goldenJokerPaysAtEndOfRound() {
        RunState g = new RunState();
        g.addJoker(JokerLibrary.create("j_golden"));
        GameEvents.endOfRound(g, new RandomStreams("R"));
        assertThat(g.money).isEqualTo(8); // 4 starting + 4
    }

    @Test
    void goldCardPaysWhenHeldAtEndOfRound() {
        RunState gc = new RunState();
        gc.hand.add(card(KING, HEARTS, Enhancement.GOLD, Seal.NONE));
        GameEvents.endOfRound(gc, new RandomStreams("R"));
        assertThat(gc.money).isEqualTo(7); // 4 + 3
    }

    @Test
    void facelessPaysOnThreeFaceCardsDiscarded() {
        RunState f = new RunState();
        f.addJoker(JokerLibrary.create("j_faceless"));
        GameEvents.preDiscard(f, new RandomStreams("R"),
                List.of(c(KING, HEARTS), c(QUEEN, SPADES), c(JACK, CLUBS)));
        assertThat(f.money).isEqualTo(9); // 4 + 5
    }

    @Test
    void facelessDoesNotPayUnderThreeFaces() {
        RunState f = new RunState();
        f.addJoker(JokerLibrary.create("j_faceless"));
        GameEvents.preDiscard(f, new RandomStreams("R"),
                List.of(c(KING, HEARTS), c(QUEEN, SPADES), c(TWO, CLUBS)));
        assertThat(f.money).isEqualTo(4);
    }

    @Test
    void constellationScalesOnPlanetUseAndAppliesInScoring() {
        RunState con = new RunState();
        con.addJoker(JokerLibrary.create("j_constellation"));
        RandomStreams rng = new RandomStreams("C");
        GameEvents.useConsumable(con, rng, "Planet");
        GameEvents.useConsumable(con, rng, "Planet");
        GameEvents.useConsumable(con, rng, "Planet");
        double s = new ScoringEngine()
                .score(List.of(c(KING, HEARTS), c(KING, SPADES)), List.of(), con, rng)
                .score();
        assertThat(s).isEqualTo(78.0); // 30 x (2 x1.3)
    }

    @Test
    void goldSealPaysWhenScored() {
        RunState gs = new RunState();
        new ScoringEngine().score(
                List.of(card(KING, HEARTS, Enhancement.NONE, Seal.GOLD), c(KING, SPADES)),
                List.of(), gs, new RandomStreams("G"));
        assertThat(gs.money).isEqualTo(7); // 4 + 3
    }

    @Test
    void luckyCardIsProbabilisticButDeterministicGivenSeed() {
        List<Card> lucky = List.of(card(KING, HEARTS, Enhancement.LUCKY, Seal.NONE), c(KING, SPADES));
        double l1 = new ScoringEngine().score(lucky, List.of(), new RunState(), new RandomStreams("LUCK")).score();
        double l2 = new ScoringEngine().score(lucky, List.of(), new RunState(), new RandomStreams("LUCK")).score();
        assertThat(l1).isEqualTo(l2);
    }
}
