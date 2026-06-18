package com.balatro.engine;

import static com.balatro.engine.TestSupport.card;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Probabilistic card effects (Lucky) read a persistent game-long hit/miss queue,
 * so the BMP fairness property holds: two players on the same seed get the exact
 * same Lucky procs across a whole run of hands — the queue advances per trigger
 * and persists across scorings, rather than re-rolling each hand.
 */
class ScoringQueueTest {

    /** Money gained from Lucky $20 procs over 6 successive hands of two Lucky Kings. */
    private long[] luckyMoneyOverHands(String seed) {
        RandomStreams rng = new RandomStreams(seed);
        RunState run = new RunState();
        run.rng = rng;
        run.queues = new QueueSet(rng); // persistent, game-long
        ScoringEngine engine = new ScoringEngine();
        List<Card> hand = List.of(
                card(KING, HEARTS, Enhancement.LUCKY, Seal.NONE),
                card(KING, SPADES, Enhancement.LUCKY, Seal.NONE));
        long[] money = new long[6];
        for (int i = 0; i < money.length; i++) {
            run.money = 0;
            engine.score(hand, List.of(), run, rng);
            money[i] = run.money; // 0, 20, or 40 depending on the queue's procs that hand
        }
        return money;
    }

    @Test
    void bothPlayersGetTheSameLuckyProcsAcrossTheRun() {
        assertThat(luckyMoneyOverHands("LUCK")).isEqualTo(luckyMoneyOverHands("LUCK"));
    }

    @Test
    void theQueuePersistsAcrossHands() {
        // Game-long, not per-hand: across 6 hands the proc pattern isn't a single
        // constant repeated (the queue advances), so at least two hands differ.
        long[] m = luckyMoneyOverHands("LUCK");
        boolean varies = false;
        for (long v : m) {
            if (v != m[0]) { varies = true; break; }
        }
        assertThat(varies).as("lucky procs vary across hands as the queue advances").isTrue();
    }
}
