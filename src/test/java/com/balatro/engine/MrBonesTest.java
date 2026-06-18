package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Rank.TWO;
import static com.balatro.engine.card.Suit.CLUBS;
import static com.balatro.engine.card.Suit.DIAMONDS;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Deck;
import com.balatro.engine.state.Ruleset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mr. Bones prevents a single blind death when at least 25% of the requirement was scored. */
class MrBonesTest {

    private final Ruleset oneHand =
            new Ruleset("OneHand", 4, 1, 3, 5, 1.0, 8, Ruleset.standard().blindBaseAmounts());
    private final Intent all = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    // Three Kings + two low cards -> Three of a Kind (~180 chips x 3), a partial-but-failing score.
    private List<Card> partialHand() {
        return List.of(c(KING, HEARTS), c(KING, SPADES), c(KING, CLUBS), c(TWO, DIAMONDS), c(TWO, CLUBS));
    }

    @Test
    void withoutMrBonesAPartialScoreLosesTheRun() {
        com.balatro.engine.game.Run run =
                new com.balatro.engine.game.Run(oneHand, "L", Deck.of(new ArrayList<>(partialHand())), jokers());
        run.play(all);
        assertThat(run.state.roundScore).isLessThan(run.requirement); // didn't clear
        assertThat(run.phase).isEqualTo(com.balatro.engine.game.Run.Phase.RUN_LOST);
    }

    @Test
    void mrBonesSavesThePartialScoreAndIsConsumed() {
        com.balatro.engine.game.Run run = new com.balatro.engine.game.Run(
                oneHand, "S", Deck.of(new ArrayList<>(partialHand())), jokers("j_mr_bones"));
        long req = run.requirement;
        run.play(all);
        assertThat(run.state.roundScore).isBetween(req / 4, req - 1); // partial, in the save window
        assertThat(run.phase).isEqualTo(com.balatro.engine.game.Run.Phase.SHOP); // survived
        assertThat(run.state.jokers()).noneMatch(j -> j.key().equals("j_mr_bones")); // consumed
    }
}
