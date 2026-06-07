package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.c;
import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static com.balatromp.engine.card.Rank.KING;
import static com.balatromp.engine.card.Suit.HEARTS;
import static com.balatromp.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import com.balatromp.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanetTest {

    @Test
    void levelingAHandRaisesItsScore() {
        RunState run = new RunState();
        List<Card> pair = List.of(c(KING, HEARTS), c(KING, SPADES));
        assertThat(new ScoringEngine().score(pair, List.of(), run, new RandomStreams("L")).score())
                .isEqualTo(60.0); // (10 base + 20 cards) x 2

        run.levelUpHand(HandType.PAIR); // +15 chips, +1 mult
        assertThat(new ScoringEngine().score(pair, List.of(), run, new RandomStreams("L")).score())
                .isEqualTo(135.0); // (25 base + 20 cards) x 3
    }

    @Test
    void usingAPlanetConsumableLevelsItsHand() {
        Run run = new Run(Ruleset.standard(), "P", stoneDeck(60), jokers());
        run.state.consumables.add("c_mercury"); // Mercury -> Pair
        assertThat(run.state.handLevel(HandType.PAIR)).isEqualTo(1);

        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.handLevel(HandType.PAIR)).isEqualTo(2);
        assertThat(run.state.consumables).isEmpty();
    }

    @Test
    void buyingAPlanetAddsItToTheConsumableInventory() {
        Run run = new Run(Ruleset.standard(), "P2", stoneDeck(200),
                jokers("j_joker", "j_joker", "j_joker"));
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Small -> SHOP
        run.state.money = 10;
        assertThat(run.shop.planets()).isNotEmpty();

        int before = run.state.consumables.size();
        assertThat(run.buyPlanet(0)).isNull();
        assertThat(run.state.consumables).hasSize(before + 1);
    }
}
