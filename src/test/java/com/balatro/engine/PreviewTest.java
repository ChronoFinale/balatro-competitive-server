package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.game.Run;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The score preview: the server projects the score for a card selection
 * authoritatively, with NO side effects (the client renders it, never computes
 * it). Crucially the result depends on the selection, so jokers whose value comes
 * from the prospective play preview correctly.
 */
class PreviewTest {

    private static Card card(Rank r, Suit s, Enhancement e) {
        return new Card(r, s, e, Edition.NONE, Seal.NONE);
    }

    private RunState runState(String seed) {
        RunState run = new RunState();
        run.rng = new RandomStreams(seed);
        run.queues = new QueueSet(run.rng);
        return run;
    }

    @Test
    void previewCommitsNothing() {
        RunState run = runState("PRE");
        run.money = 5;
        Card glass = card(Rank.KING, Suit.HEARTS, Enhancement.GLASS);
        Card lucky = card(Rank.KING, Suit.SPADES, Enhancement.LUCKY); // pair of Kings
        List<Card> played = List.of(glass, lucky, card(Rank.TWO, Suit.CLUBS, Enhancement.NONE),
                card(Rank.THREE, Suit.CLUBS, Enhancement.NONE), card(Rank.FOUR, Suit.DIAMONDS, Enhancement.NONE));

        ScoreResult p1 = new ScoringEngine().preview(played, List.of(), run, run.rng);
        assertThat(p1.score()).isGreaterThan(0);
        assertThat(run.money).isEqualTo(5);      // no money credited
        assertThat(glass.destroyed).isFalse();   // no Glass break
        assertThat(glass.enhancement).isEqualTo(Enhancement.GLASS); // not mutated

        // repeated previews are identical — the real RNG queues were never advanced
        ScoreResult p2 = new ScoringEngine().preview(played, List.of(), run, run.rng);
        assertThat(p2.score()).isEqualTo(p1.score());
    }

    @Test
    void previewDependsOnTheSelection() {
        RunState run = runState("PRE");
        run.addJoker(JokerLibrary.create("j_greedy_joker")); // +3 Mult per played Diamond
        List<Card> diamonds = List.of(card(Rank.NINE, Suit.DIAMONDS, Enhancement.NONE),
                card(Rank.NINE, Suit.DIAMONDS, Enhancement.NONE), card(Rank.TWO, Suit.CLUBS, Enhancement.NONE),
                card(Rank.THREE, Suit.CLUBS, Enhancement.NONE), card(Rank.FOUR, Suit.SPADES, Enhancement.NONE));
        List<Card> spades = List.of(card(Rank.NINE, Suit.SPADES, Enhancement.NONE),
                card(Rank.NINE, Suit.SPADES, Enhancement.NONE), card(Rank.TWO, Suit.CLUBS, Enhancement.NONE),
                card(Rank.THREE, Suit.CLUBS, Enhancement.NONE), card(Rank.FOUR, Suit.SPADES, Enhancement.NONE));

        double withDiamonds = new ScoringEngine().preview(diamonds, List.of(), run, run.rng).mult();
        double withoutDiamonds = new ScoringEngine().preview(spades, List.of(), run, run.rng).mult();
        assertThat(withDiamonds - withoutDiamonds).isEqualTo(6.0); // 2 scoring Diamonds x +3
    }

    @Test
    void runPreviewScoreLeavesTheRunUntouched() {
        Run run = new Run(com.balatro.engine.state.Ruleset.standard(), "PRE");
        int money = run.state.money;
        int handSize = run.state.hand.size();
        ScoreResult pre = run.previewScore(List.of(0, 1, 2, 3, 4));
        assertThat(pre).isNotNull();
        assertThat(run.phase).isEqualTo(Run.Phase.BLIND_SELECT); // not advanced
        assertThat(run.state.money).isEqualTo(money);
        assertThat(run.state.hand).hasSize(handSize);
        assertThat(run.state.roundScore).isEqualTo(0); // preview didn't score the round
    }
}
