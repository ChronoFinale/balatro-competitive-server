package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.c;
import static com.balatromp.engine.card.Rank.KING;
import static com.balatromp.engine.card.Rank.QUEEN;
import static com.balatromp.engine.card.Suit.HEARTS;
import static com.balatromp.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.GameEvents;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.joker.Trigger;
import com.balatromp.engine.rng.QueueSet;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/** LEVEL_UP_HAND effect: Burnt (first discard) and Space (chance on play). */
class LevelUpJokerTest {

    private RunState freshRun(String jokerKey) {
        RunState run = new RunState();
        run.rng = new RandomStreams("LVL");
        run.queues = new QueueSet(run.rng);
        run.addJoker(JokerLibrary.create(jokerKey));
        return run;
    }

    @Test
    void burntLevelsOnlyTheFirstDiscardEachRound() {
        RunState run = freshRun("j_burnt");
        GameEvents.raise(Trigger.BLIND_SELECTED, run, run.rng, null); // reset counter
        int before = run.handLevel(HandType.PAIR);

        GameEvents.preDiscard(run, run.rng, List.of(c(KING, HEARTS), c(KING, SPADES)));
        assertThat(run.handLevel(HandType.PAIR)).as("first discard levels the pair").isEqualTo(before + 1);

        GameEvents.preDiscard(run, run.rng, List.of(c(QUEEN, HEARTS), c(QUEEN, SPADES)));
        assertThat(run.handLevel(HandType.PAIR)).as("second discard does not").isEqualTo(before + 1);
    }

    @Test
    void spaceEventuallyUpgradesThePlayedHand() {
        RunState run = freshRun("j_space");
        int before = run.handLevel(HandType.PAIR);
        ScoringEngine eng = new ScoringEngine();
        for (int i = 0; i < 40; i++) {
            eng.score(List.of(c(KING, HEARTS), c(KING, SPADES)), List.of(), run, run.rng);
        }
        assertThat(run.handLevel(HandType.PAIR)).as("1-in-4 over 40 plays upgrades the pair").isGreaterThan(before);
    }
}
