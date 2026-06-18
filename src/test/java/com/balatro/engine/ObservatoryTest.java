package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Observatory: holding a hand's Planet gives that hand x1.5 Mult (resolved by Run, applied by the scorer). */
class ObservatoryTest {

    @Test
    void heldPlanetMultipliesItsOwnHand() {
        List<Card> pair = List.of(c(KING, HEARTS), c(KING, SPADES));
        double base = new ScoringEngine().score(pair, List.of(), new RunState(), new RandomStreams("OBS")).score();

        RunState obs = new RunState();              // Run.resolveObservatory() sets these from owned vouchers/consumables
        obs.heldPlanetMult = 1.5;
        obs.heldPlanetHands.add(HandType.PAIR);
        double boosted = new ScoringEngine().score(pair, List.of(), obs, new RandomStreams("OBS")).score();

        assertThat(boosted).isEqualTo(base * 1.5);
    }

    @Test
    void noBoostForAHandWhosePlanetIsNotHeld() {
        List<Card> pair = List.of(c(KING, HEARTS), c(KING, SPADES));
        double base = new ScoringEngine().score(pair, List.of(), new RunState(), new RandomStreams("OBS")).score();

        RunState obs = new RunState();
        obs.heldPlanetMult = 1.5;
        obs.heldPlanetHands.add(HandType.FLUSH);    // hold the Flush planet, but played a Pair
        double same = new ScoringEngine().score(pair, List.of(), obs, new RandomStreams("OBS")).score();

        assertThat(same).isEqualTo(base);
    }
}
