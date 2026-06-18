package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Blinds.BlindType;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The newly-wired vouchers: Hieroglyph (-1 hand, folds), Director's Cut (boss reroll, $10, 1/ante). */
class VoucherEffectsTest {

    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    private static Run atBoss(String voucher) {
        Run run = new Run(Ruleset.standard(), "VE", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_joker"));
        run.state.vouchers.add(voucher);
        run.play(FIVE); run.proceed(); // Small -> Big
        run.play(FIVE); run.proceed(); // Big -> Boss (BLIND_SELECT)
        return run;
    }

    @Test
    void hieroglyphFoldsMinusOneHandIntoEveryRound() {
        Run run = atBoss("v_hieroglyph");
        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        // ruleset hands (4) - 1 from Hieroglyph, folded with the boss's own resource mods.
        assertThat(run.state.handsLeft).isLessThanOrEqualTo(Ruleset.standard().hands() - 1);
    }

    @Test
    void directorsCutRerollsTheBossOncePerAnte() {
        Run run = atBoss("v_directors_cut");
        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        run.state.money = 100;
        assertThat(run.rerollBoss()).isNull();                          // first reroll: $10
        assertThat(run.state.money).isEqualTo(90);
        assertThat(run.rerollBoss()).isEqualTo("no boss rerolls left"); // Director's Cut: 1 per ante
    }

    @Test
    void retconRerollsTheBossUnlimited() {
        Run run = atBoss("v_retcon");
        run.state.money = 100;
        assertThat(run.rerollBoss()).isNull();
        assertThat(run.rerollBoss()).isNull(); // Retcon: no per-ante limit
        assertThat(run.state.money).isEqualTo(80);
    }
}
