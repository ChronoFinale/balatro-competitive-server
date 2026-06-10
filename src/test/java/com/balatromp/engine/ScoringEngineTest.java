package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.c;
import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.poly;
import static com.balatromp.engine.TestSupport.score;
import static com.balatromp.engine.card.Rank.ACE;
import static com.balatromp.engine.card.Rank.EIGHT;
import static com.balatromp.engine.card.Rank.FIVE;
import static com.balatromp.engine.card.Rank.JACK;
import static com.balatromp.engine.card.Rank.KING;
import static com.balatromp.engine.card.Rank.NINE;
import static com.balatromp.engine.card.Rank.QUEEN;
import static com.balatromp.engine.card.Rank.SEVEN;
import static com.balatromp.engine.card.Rank.SIX;
import static com.balatromp.engine.card.Rank.TEN;
import static com.balatromp.engine.card.Rank.THREE;
import static com.balatromp.engine.card.Suit.CLUBS;
import static com.balatromp.engine.card.Suit.DIAMONDS;
import static com.balatromp.engine.card.Suit.HEARTS;
import static com.balatromp.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoreResult;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringEngineTest {

    @Test
    void pairOfKingsPlusJoker() { // 30 chips x (2+4) = 180
        assertThat(score(jokers("j_joker"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(180.0);
    }

    @Test
    void jokerEditions() { // Foil +50 chips, Holo +10 mult (both before the joker scores), Poly x1.5 after
        var played = List.of(c(KING, HEARTS), c(KING, SPADES)); // pair + j_joker baseline: 30 x 6 = 180
        assertThat(editionScore(com.balatromp.engine.card.Edition.FOIL, played).score()).isEqualTo(80.0 * 6); // (30+50) x 6
        assertThat(editionScore(com.balatromp.engine.card.Edition.HOLOGRAPHIC, played).score()).isEqualTo(30.0 * 16); // 30 x (6+10)
        assertThat(editionScore(com.balatromp.engine.card.Edition.POLYCHROME, played).score()).isEqualTo(180.0 * 1.5); // 30 x 6 x1.5
    }

    private static ScoreResult editionScore(com.balatromp.engine.card.Edition ed, List<Card> played) {
        RunState run = new RunState();
        var j = com.balatromp.engine.joker.JokerLibrary.create("j_joker");
        run.addJoker(j);
        run.setJokerEdition(j, ed);
        return new ScoringEngine().score(played, List.of(), run, new RandomStreams("T"));
    }

    @Test
    void heartFlush() { // (35+50) x 4 = 340
        assertThat(score(jokers(), List.of(c(ACE, HEARTS), c(KING, HEARTS), c(QUEEN, HEARTS),
                c(JACK, HEARTS), c(NINE, HEARTS))).score()).isEqualTo(340.0);
    }

    @Test
    void polychromeAppliesBeforeJokerMult() { // chips 30; (2 x1.5)=3, +4 = 7 -> 210
        assertThat(score(jokers("j_joker"), List.of(poly(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(210.0);
    }

    @Test
    void slyJokerAddsChipsOnPair() { // (30+50) x 2 = 160
        assertThat(score(jokers("j_sly_joker"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(160.0);
    }

    @Test
    void halfJokerOnSmallHand() { // 30 x (2+20) = 660
        assertThat(score(jokers("j_half"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(660.0);
    }

    @Test
    void hackRetriggersLowCards() { // pair of 3s scored twice: (10 + 12) x 2 = 44
        assertThat(score(jokers("j_hack"), List.of(c(THREE, HEARTS), c(THREE, SPADES))).score())
                .isEqualTo(44.0);
    }

    @Test
    void blueprintCopiesRightNeighbour() { // Blueprint + Joker: +4 +4 -> 30 x 10 = 300
        assertThat(score(jokers("j_blueprint", "j_joker"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(300.0);
    }

    @Test
    void brainstormCopiesLeftmostJoker() { // Joker + Brainstorm(copies leftmost): +4 +4 -> 30 x 10 = 300
        assertThat(score(jokers("j_joker", "j_brainstorm"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(300.0);
    }

    @Test
    void cavendishTriplesMult() { // pair of Kings: 30 x (2 x3) = 180
        assertThat(score(jokers("j_cavendish"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(180.0);
    }

    @Test
    void tribouletDoublesPerKingOrQueen() { // 30 x (2 x2 x2) = 240
        assertThat(score(jokers("j_triboulet"), List.of(c(KING, HEARTS), c(KING, SPADES))).score())
                .isEqualTo(240.0);
    }

    @Test
    void roughGemCreditsMoneyOnScoreButNotOnPreview() {
        RunState run = new RunState();
        run.money = 10;
        run.addJoker(com.balatromp.engine.joker.JokerLibrary.create("j_rough_gem"));
        RandomStreams rng = new RandomStreams("GEM");
        // two played Diamonds -> +$2; the other three cards earn nothing
        List<Card> hand = List.of(c(NINE, com.balatromp.engine.card.Suit.DIAMONDS),
                c(NINE, com.balatromp.engine.card.Suit.DIAMONDS), c(THREE, SPADES),
                c(KING, SPADES), c(QUEEN, HEARTS));
        ScoringEngine eng = new ScoringEngine();
        eng.preview(hand, List.of(), run, rng);
        assertThat(run.money).as("preview is a dry-run, no money credited").isEqualTo(10);
        eng.score(hand, List.of(), run, rng);
        assertThat(run.money).as("two scored Diamonds give $1 each").isEqualTo(12);
    }

    @Test
    void goldenTicketPaysForScoredGoldCards() {
        RunState run = new RunState();
        run.money = 0;
        run.addJoker(com.balatromp.engine.joker.JokerLibrary.create("j_golden_ticket"));
        RandomStreams rng = new RandomStreams("GT");
        com.balatromp.engine.card.Card gold = new com.balatromp.engine.card.Card(
                KING, HEARTS, com.balatromp.engine.card.Enhancement.GOLD,
                com.balatromp.engine.card.Edition.NONE, com.balatromp.engine.card.Seal.NONE);
        new ScoringEngine().score(List.of(gold, c(KING, SPADES)), List.of(), run, rng);
        assertThat(run.money).as("one scored Gold card pays $4").isEqualTo(4);
    }

    @Test
    void greenJokerGrowsPerHandAndShrinksPerDiscard() {
        RunState run = new RunState();
        var gj = com.balatromp.engine.joker.JokerLibrary.create("j_green_joker");
        run.addJoker(gj);
        RandomStreams rng = new RandomStreams("GREEN");
        ScoringEngine eng = new ScoringEngine();
        List<Card> hand = List.of(c(KING, HEARTS), c(KING, SPADES));
        eng.score(hand, List.of(), run, rng);
        eng.score(hand, List.of(), run, rng);
        assertThat(run.jokerState(gj).get("m")).as("+1 per hand played").isEqualTo(2);
        com.balatromp.engine.game.GameEvents.preDiscard(run, rng, List.of(c(THREE, SPADES)));
        assertThat(run.jokerState(gj).get("m")).as("-1 per discard").isEqualTo(1);
        // Preview must NOT advance the counter.
        eng.preview(hand, List.of(), run, rng);
        assertThat(run.jokerState(gj).get("m")).as("preview is a dry-run").isEqualTo(1);
    }

    @Test
    void fortuneTellerCountsTarotsUsed() {
        RunState run = new RunState();
        var ft = com.balatromp.engine.joker.JokerLibrary.create("j_fortune_teller");
        run.addJoker(ft);
        RandomStreams rng = new RandomStreams("FT");
        com.balatromp.engine.game.GameEvents.useConsumable(run, rng, "Tarot");
        com.balatromp.engine.game.GameEvents.useConsumable(run, rng, "Planet"); // not a Tarot
        assertThat(run.jokerState(ft).get("tarots")).isEqualTo(1);
    }

    @Test
    void hangingChadHasASinglePlayerAndMultiplayerVariant() {
        // A straight (5,6,7,8,9) distinguishes the variants: SP triggers the first card 3x,
        // MP triggers the first AND second 2x each -- same total retriggers, different cards.
        List<Card> hand = List.of(c(FIVE, HEARTS), c(SIX, SPADES), c(SEVEN, CLUBS),
                c(EIGHT, DIAMONDS), c(NINE, HEARTS));
        RunState sp = new RunState();
        sp.addJoker(com.balatromp.engine.joker.JokerLibrary.create("j_hanging_chad", "default"));
        RunState mp = new RunState();
        mp.addJoker(com.balatromp.engine.joker.JokerLibrary.create("j_hanging_chad", "multiplayer"));
        double spScore = new ScoringEngine().score(hand, List.of(), sp, new RandomStreams("V")).score();
        double mpScore = new ScoringEngine().score(hand, List.of(), mp, new RandomStreams("V")).score();
        assertThat(spScore).as("SP: first card 3x -> 75 chips x4").isEqualTo(300.0);
        assertThat(mpScore).as("MP: first + second card 2x -> 76 chips x4").isEqualTo(304.0);
    }

    @Test
    void rideTheBusScalesAcrossConsecutiveHands() {
        RunState run = new RunState();
        run.addJoker(com.balatromp.engine.joker.JokerLibrary.create("j_ride_the_bus"));
        RandomStreams rng = new RandomStreams("RTB");
        List<Card> tens = List.of(c(TEN, HEARTS), c(TEN, SPADES));
        ScoringEngine eng = new ScoringEngine();
        assertThat(eng.score(tens, List.of(), run, rng).score()).isEqualTo(90.0);  // streak 1
        assertThat(eng.score(tens, List.of(), run, rng).score()).isEqualTo(120.0); // streak 2
    }
}
