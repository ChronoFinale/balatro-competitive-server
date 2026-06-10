package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.c;
import static com.balatromp.engine.card.Rank.NINE;
import static com.balatromp.engine.card.Suit.CLUBS;
import static com.balatromp.engine.card.Suit.HEARTS;
import static com.balatromp.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.GameEvents;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.state.RunState;
import org.junit.jupiter.api.Test;

/** End-of-round economy jokers: payouts credited when END_OF_ROUND is raised. */
class EconomyJokerTest {

    private final RandomStreams rng = new RandomStreams("ECON");

    @Test
    void cloud9PaysPerNineInDeck() {
        RunState run = new RunState();
        run.money = 0;
        run.deckComposition.add(c(NINE, HEARTS));
        run.deckComposition.add(c(NINE, SPADES));
        run.deckComposition.add(c(NINE, CLUBS));
        run.addJoker(JokerLibrary.create("j_cloud_9"));
        GameEvents.endOfRound(run, rng, false);
        assertThat(run.money).isEqualTo(3);
    }

    @Test
    void rocketGrowsPerBossDefeated() {
        RunState run = new RunState();
        run.money = 0;
        run.addJoker(JokerLibrary.create("j_rocket"));
        GameEvents.endOfRound(run, rng, false); // non-boss: $1 (0 bosses)
        assertThat(run.money).isEqualTo(1);
        GameEvents.endOfRound(run, rng, true);  // boss: count->1, payout 1 + 2*1 = $3
        assertThat(run.money).isEqualTo(4);
    }

    @Test
    void todoListPaysWhenTheRoundHandIsPlayed() {
        RunState run = new RunState();
        run.money = 0;
        run.todoHandType = com.balatromp.engine.hand.HandType.PAIR;
        run.queues = new com.balatromp.engine.rng.QueueSet(rng);
        run.addJoker(JokerLibrary.create("j_todo_list"));
        // Play a pair (matches the round hand) -> $4.
        new com.balatromp.engine.scoring.ScoringEngine().score(
                java.util.List.of(c(NINE, HEARTS), c(NINE, SPADES)), java.util.List.of(), run, rng);
        assertThat(run.money).isEqualTo(4);
    }

    @Test
    void mailInRebatePaysPerDiscardedCardOfTheRoundRank() {
        RunState run = new RunState();
        run.money = 0;
        run.rebateRankId = 9; // nines this round
        run.queues = new com.balatromp.engine.rng.QueueSet(rng);
        run.addJoker(JokerLibrary.create("j_mail_in_rebate"));
        // Discard two 9s and a King -> $3 x 2 = $6.
        GameEvents.preDiscard(run, rng, java.util.List.of(c(NINE, HEARTS), c(NINE, SPADES),
                new com.balatromp.engine.card.Card(com.balatromp.engine.card.Rank.KING, HEARTS,
                        com.balatromp.engine.card.Enhancement.NONE, com.balatromp.engine.card.Edition.NONE,
                        com.balatromp.engine.card.Seal.NONE)));
        assertThat(run.money).isEqualTo(6);
    }

    @Test
    void satellitePaysPerUniquePlanetUsed() {
        RunState run = new RunState();
        run.money = 0;
        run.planetsUsedThisRun.add("c_pluto");
        run.planetsUsedThisRun.add("c_mercury");
        run.planetsUsedThisRun.add("c_pluto"); // duplicate -> still 2 unique
        run.addJoker(JokerLibrary.create("j_satellite"));
        GameEvents.endOfRound(run, rng, false);
        assertThat(run.money).isEqualTo(2);
    }

    @Test
    void delayedGratificationPaysOnlyIfNoDiscardsUsed() {
        RunState run = new RunState();
        run.money = 0;
        run.discardsLeft = 3;
        run.discardsUsedThisRound = 0;
        Joker dg = JokerLibrary.create("j_delayed_gratification");
        run.addJoker(dg);
        GameEvents.endOfRound(run, rng, false);
        assertThat(run.money).as("$2 per remaining discard").isEqualTo(6);

        run.money = 0;
        run.discardsUsedThisRound = 1; // a discard was used -> no payout
        GameEvents.endOfRound(run, rng, false);
        assertThat(run.money).isZero();
    }
}
