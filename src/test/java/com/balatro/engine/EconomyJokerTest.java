package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.card.Rank.NINE;
import static com.balatro.engine.card.Suit.CLUBS;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.GameEvents;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.state.RunState;
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
        run.roundTargets.put("todoHand", com.balatro.engine.hand.HandType.PAIR);
        run.queues = new com.balatro.engine.rng.QueueSet(rng);
        run.addJoker(JokerLibrary.create("j_todo_list"));
        // Play a pair (matches the round hand) -> $4.
        new com.balatro.engine.scoring.ScoringEngine().score(
                java.util.List.of(c(NINE, HEARTS), c(NINE, SPADES)), java.util.List.of(), run, rng);
        assertThat(run.money).isEqualTo(4);
    }

    @Test
    void mailInRebatePaysPerDiscardedCardOfTheRoundRank() {
        RunState run = new RunState();
        run.money = 0;
        run.roundTargets.put("rebateRankId", 9); // nines this round
        run.queues = new com.balatro.engine.rng.QueueSet(rng);
        run.addJoker(JokerLibrary.create("j_mail_in_rebate"));
        // Discard two 9s and a King -> $5 x 2 = $10 (real Balatro pays $5/card).
        GameEvents.preDiscard(run, rng, java.util.List.of(c(NINE, HEARTS), c(NINE, SPADES),
                new com.balatro.engine.card.Card(com.balatro.engine.card.Rank.KING, HEARTS,
                        com.balatro.engine.card.Enhancement.NONE, com.balatro.engine.card.Edition.NONE,
                        com.balatro.engine.card.Seal.NONE)));
        assertThat(run.money).isEqualTo(10);
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

    @Test
    void creditCardDebtFloorIsDerivedFromOwnedJokers() {
        // The money floor is no longer a hardcoded joker check — it's folded into the derived economy
        // alongside To the Moon and the interest vouchers (a system joker expressed as data).
        var deck = com.balatro.engine.game.DeckCatalog.get("d_base");
        var noCredit = com.balatro.engine.game.EconomyConfig.resolve(deck, java.util.Set.of(), java.util.List.of());
        assertThat(noCredit.minMoney()).isZero();

        var withCredit = com.balatro.engine.game.EconomyConfig.resolve(
                deck, java.util.Set.of(), java.util.List.of(JokerLibrary.create("j_credit_card")));
        assertThat(withCredit.minMoney()).isEqualTo(-20); // Credit Card: up to $20 of debt
    }
}
