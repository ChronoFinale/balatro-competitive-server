package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static com.balatro.engine.card.Rank.ACE;
import static com.balatro.engine.card.Rank.FIVE;
import static com.balatro.engine.card.Rank.FOUR;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Rank.SIX;
import static com.balatro.engine.card.Rank.SEVEN;
import static com.balatro.engine.card.Rank.THREE;
import static com.balatro.engine.card.Rank.TWO;
import static com.balatro.engine.card.Rank.EIGHT;
import static com.balatro.engine.card.Rank.NINE;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static com.balatro.engine.card.Suit.CLUBS;
import static com.balatro.engine.card.Suit.DIAMONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Run;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import com.balatro.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Consumable-creation jokers add cards to the run server-side. */
class CreationJokerTest {

    @Test
    void cartomancerCreatesATarotAtBlindSelect() {
        Run run = new Run(Ruleset.standard(), "C", stoneDeck(400), jokers("j_cartomancer"));
        // startBlind raised BLIND_SELECTED, so Cartomancer should have made one Tarot.
        assertThat(run.state.consumables).hasSize(1);
        assertThat(run.state.consumables.get(0)).startsWith("c_");
    }

    @Test
    void cartomancerRespectsConsumableSlots() {
        Run run = new Run(Ruleset.standard(), "C", stoneDeck(400),
                jokers("j_cartomancer", "j_cartomancer", "j_cartomancer"));
        // Two slots: three Cartomancers at one blind select can fill at most the slots.
        assertThat(run.state.consumables.size()).isLessThanOrEqualTo(run.state.consumableSlots);
    }

    @Test
    void vagabondCreatesTarotOnlyWhenBroke() {
        RunState poor = run("j_vagabond", 4);
        score(poor, List.of(c(KING, HEARTS), c(KING, SPADES)));
        assertThat(poor.consumables).as("$4 -> create").hasSize(1);

        RunState rich = run("j_vagabond", 10);
        score(rich, List.of(c(KING, HEARTS), c(KING, SPADES)));
        assertThat(rich.consumables).as("$10 -> no create").isEmpty();
    }

    @Test
    void superpositionCreatesOnAceStraight() {
        RunState run = run("j_superposition", 4);
        score(run, List.of(c(ACE, HEARTS), c(TWO, SPADES), c(THREE, CLUBS), c(FOUR, DIAMONDS), c(FIVE, HEARTS)));
        assertThat(run.consumables).hasSize(1);
    }

    @Test
    void seanceCreatesOnStraightFlush() {
        RunState run = run("j_seance", 4);
        score(run, List.of(c(FIVE, HEARTS), c(SIX, HEARTS), c(SEVEN, HEARTS), c(EIGHT, HEARTS), c(NINE, HEARTS)));
        assertThat(run.consumables).hasSize(1);
        assertThat(run.consumables.get(0)).startsWith("c_");
    }

    @Test
    void previewNeverCreates() {
        RunState run = run("j_vagabond", 4);
        new ScoringEngine().preview(List.of(c(KING, HEARTS), c(KING, SPADES)), List.of(), run, run.rng);
        assertThat(run.consumables).as("preview is a dry-run").isEmpty();
    }

    @Test
    void marbleAddsAStoneCardToTheDeck() {
        RunState run = run("j_marble", 4);
        int before = run.deckComposition.size();
        com.balatro.engine.game.GameEvents.raise(
                com.balatro.grammar.Trigger.BLIND_SELECTED, run, run.rng, null);
        assertThat(run.deckComposition).hasSize(before + 1);
        assertThat(run.deckComposition.get(before).isStone()).isTrue();
    }

    @Test
    void riffRaffCreatesTwoCommonJokers() {
        RunState run = run("j_riff_raff", 4); // 1 joker to start
        com.balatro.engine.game.GameEvents.raise(
                com.balatro.grammar.Trigger.BLIND_SELECTED, run, run.rng, null);
        assertThat(run.jokers()).hasSize(3); // +2 common
    }

    @Test
    void sixthSenseDestroysASingleSixAndCreatesASpectral() {
        RunState run = run("j_sixth_sense", 4);
        com.balatro.engine.card.Card six = c(SIX, HEARTS);
        new ScoringEngine().score(List.of(six), List.of(), run, run.rng);
        assertThat(six.destroyed).as("the played 6 is destroyed").isTrue();
        assertThat(run.consumables).as("a Spectral is created").hasSize(1);
    }

    @Test
    void dnaCopiesASingleCardOnTheFirstHand() {
        RunState run = run("j_dna", 4);
        int before = run.deckComposition.size();
        new ScoringEngine().score(List.of(c(KING, HEARTS)), List.of(), run, run.rng);
        assertThat(run.deckComposition).as("a copy is added to the deck").hasSize(before + 1);
        // not the first hand any more -> no copy
        run.handsPlayedThisRound = 1;
        new ScoringEngine().score(List.of(c(KING, HEARTS)), List.of(), run, run.rng);
        assertThat(run.deckComposition).hasSize(before + 1);
    }

    @Test
    void certificateAddsASealedCardOnFirstHandDrawn() {
        Run run = new Run(Ruleset.standard(), "C", stoneDeck(400), jokers("j_certificate"));
        // The blind setup raised FIRST_HAND_DRAWN, so Certificate added one card; it carries a (non-NONE) seal.
        long sealed = run.state.deckComposition.stream()
                .filter(cc -> cc.seal != com.balatro.engine.card.Seal.NONE).count();
        assertThat(sealed).isGreaterThanOrEqualTo(1);
    }

    private static RunState run(String jokerKey, int money) {
        RunState run = new RunState();
        run.money = money;
        run.rng = new RandomStreams("CREATE");
        run.queues = new com.balatro.engine.rng.QueueSet(run.rng);
        run.addJoker(JokerLibrary.create(jokerKey));
        return run;
    }

    private static void score(RunState run, List<com.balatro.engine.card.Card> played) {
        new ScoringEngine().score(played, List.of(), run, run.rng);
    }
}
