package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Blinds.BlindType;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The pre-blind Select/Skip flow: a run pauses at BLIND_SELECT before each blind.
 * You Select (play) it, or Skip a Small/Big blind for a tag (Boss can't be skipped).
 * Playing auto-selects (so older flows still work). Beating a blind records a cash-out
 * breakdown for the end-of-round screen.
 */
class BlindSelectTest {

    private static final Ruleset STD = Ruleset.standard();
    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    private Run run() {
        return new Run(STD, "SELECT", stoneDeck(300), jokers("j_joker", "j_joker", "j_joker"));
    }

    @Test
    void runStartsAtTheBlindSelectScreen() {
        Run run = run();
        assertThat(run.phase).isEqualTo(Run.Phase.BLIND_SELECT);
        assertThat(run.blind).isEqualTo(BlindType.SMALL);
    }

    @Test
    void selectBlindEntersTheActiveBlind() {
        Run run = run();
        assertThat(run.selectBlind()).isNull();
        assertThat(run.phase).isEqualTo(Run.Phase.BLIND_ACTIVE);
        assertThat(run.selectBlind()).isEqualTo("not selecting a blind"); // already selected
    }

    @Test
    void playingAutoSelectsTheBlind() {
        Run run = run(); // BLIND_SELECT
        run.play(FIVE);  // auto-selects, then scores
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);
    }

    @Test
    void skippingASmallBlindGrantsATagAndAdvancesToTheBig() {
        Run run = run();
        assertThat(run.skipBlind()).isNull();
        assertThat(run.state.blindsSkipped).isEqualTo(1);
        assertThat(run.blind).isEqualTo(BlindType.BIG);
        assertThat(run.phase).isEqualTo(Run.Phase.BLIND_SELECT); // back to select for the next blind
    }

    @Test
    void theBossBlindCannotBeSkipped() {
        Run run = run();
        run.skipBlind(); // Small -> Big
        run.skipBlind(); // Big -> Boss
        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        assertThat(run.skipBlind()).isEqualTo("cannot skip this blind");
    }

    @Test
    void beatingABlindRecordsTheCashOutBreakdown() {
        Run run = run();
        run.play(FIVE); // clear Small -> shop
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);
        assertThat(((Number) run.view().counters().get("cashOutReward")).intValue())
                .isEqualTo(BlindType.SMALL.reward); // Small blind's $3 reward in the breakdown
    }
}
