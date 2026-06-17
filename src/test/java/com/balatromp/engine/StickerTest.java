package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.c;
import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static com.balatromp.engine.card.Rank.KING;
import static com.balatromp.engine.card.Suit.HEARTS;
import static com.balatromp.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import com.balatromp.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Stake-granted joker stickers (validated vs game.lua / card.lua):
 * Eternal (can't be sold/destroyed), Perishable (debuffed after {@code PERISHABLE_ROUNDS}=5),
 * Rental ($3/round). Stickers live in the joker's server-only state bag.
 */
class StickerTest {

    private static final Ruleset STD = Ruleset.standard();
    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    @Test
    void eternalJokerCannotBeSold() {
        Run run = new Run(STD, "E", stoneDeck(300), jokers("j_joker"));
        run.state.jokerState(run.state.jokers().get(0)).put("eternal", true);
        assertThat(run.sellJoker(0)).isEqualTo("eternal jokers cannot be sold");
        assertThat(run.state.jokers()).hasSize(1); // still owned
    }

    @Test
    void perishedJokerContributesNothingToScoring() {
        // j_joker = +4 Mult. Pair of Kings: 30 chips x (2+4) = 180; debuffed -> 30 x 2 = 60.
        RunState run = new RunState();
        jokers("j_joker").forEach(run::addJoker);
        var played = List.of(c(KING, HEARTS), c(KING, SPADES));
        assertThat(new ScoringEngine().score(played, List.of(), run, new RandomStreams("X")).score())
                .isEqualTo(180.0);
        run.jokerState(run.jokers().get(0)).put("debuffed", true);
        assertThat(new ScoringEngine().score(played, List.of(), run, new RandomStreams("X")).score())
                .isEqualTo(60.0); // the joker's +4 Mult is gone
    }

    @Test
    void perishableCountsDownEachRoundAndDebuffsAtZero() {
        Run run = new Run(STD, "P", stoneDeck(300), jokers("j_joker", "j_joker", "j_joker"));
        var st = run.state.jokerState(run.state.jokers().get(0));
        st.put("perishable", true);
        st.put("perishTally", 1); // one round left
        run.play(FIVE);           // win Small -> end-of-round upkeep: tally 0 -> debuffed
        assertThat(((Number) st.get("perishTally")).intValue()).isEqualTo(0);
        assertThat(st.get("debuffed")).isEqualTo(true);
    }

    @Test
    void rentalJokerChargesThreeDollarsPerRound() {
        // Small blind: +$3 reward, $0 interest at $4. A $3 rent nets the reward to zero.
        Run rental = new Run(STD, "R", stoneDeck(300), jokers("j_joker", "j_joker", "j_joker"));
        rental.state.jokerState(rental.state.jokers().get(0)).put("rental", true);
        int before = rental.state.money;
        rental.play(FIVE); // win Small
        assertThat(rental.state.money).isEqualTo(before); // $3 reward eaten by $3 rent

        Run normal = new Run(STD, "R", stoneDeck(300), jokers("j_joker", "j_joker", "j_joker"));
        int before2 = normal.state.money;
        normal.play(FIVE);
        assertThat(normal.state.money).isEqualTo(before2 + 3); // keeps the $3 reward
    }
}
