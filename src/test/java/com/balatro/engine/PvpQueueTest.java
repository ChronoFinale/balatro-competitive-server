package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Deck;
import com.balatro.engine.state.Ruleset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * In a PvP blind the probabilistic queues (Lucky/Glass/Bloodstone/…) are per-ante and
 * RESET at the start of each hand, so two equal hands proc identically regardless of
 * how many hands are left — the fairness property the Attrition duel needs. Outside
 * PvP the queues are game-long and advance hand to hand.
 */
class PvpQueueTest {

    private static final Ruleset STD = Ruleset.standard();
    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    /** A deck of Lucky aces — every scored card rolls the Lucky mult/money queues. */
    private static Deck luckyDeck(int n) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cards.add(new Card(Rank.ACE, Suit.HEARTS, Enhancement.LUCKY, Edition.NONE, Seal.NONE));
        }
        return Deck.of(cards);
    }

    private Run luckyRun(String seed) {
        Run run = new Run(STD, seed, luckyDeck(80), jokers());
        run.requirement = 10_000_000; // never clear the blind, so we can keep playing hands
        run.state.handsLeft = 20;
        return run;
    }

    @Test
    void pvpQueueResetsEachHandSoEqualHandsScoreEqually() {
        Run run = luckyRun("PVPQ");
        run.state.inPvpBlind = true; // route + reset the per-ante PvP queues
        double s1 = run.play(FIVE).score().score();
        double s2 = run.play(FIVE).score().score();
        double s3 = run.play(FIVE).score().score();
        assertThat(s2).isEqualTo(s1); // each hand replays the PvP queue from the start
        assertThat(s3).isEqualTo(s1);
    }

    @Test
    void normalBlindQueueAdvancesAcrossHands() {
        Run run = luckyRun("PVPQ"); // inPvpBlind = false
        Set<Double> scores = new HashSet<>();
        for (int i = 0; i < 8; i++) scores.add(run.play(FIVE).score().score());
        // The game-long Lucky queue advances each hand, so equal hands don't all score the same.
        assertThat(scores.size()).isGreaterThan(1);
    }

    @Test
    void pvpIsFairBetweenTwoPlayersOnTheSameSeed() {
        Run a = luckyRun("FAIR");
        a.state.inPvpBlind = true;
        Run b = luckyRun("FAIR");
        b.state.inPvpBlind = true;
        assertThat(a.play(FIVE).score().score()).isEqualTo(b.play(FIVE).score().score());
    }
}
