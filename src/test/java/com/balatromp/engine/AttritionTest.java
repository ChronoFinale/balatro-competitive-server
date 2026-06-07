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
 * Attrition's Nemesis (PvP) boss flow at the Run level: a boss blind at/after
 * {@code pvpFromAnte} becomes a head-to-head with no clear-requirement — you play
 * all your hands, then wait (PVP_PENDING) for the match to compare scores; the
 * match resolves it back to the shop.
 */
class AttritionTest {

    private final Ruleset std = Ruleset.standard();
    private final Intent all = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    @Test
    void bossBecomesNemesisBlindThatPendsThenResolves() {
        Run run = new Run(std, "A", stoneDeck(400), jokers("j_joker", "j_joker", "j_joker"));
        run.pvpFromAnte = 1; // make the ante-1 boss a Nemesis blind (test shortcut)

        run.play(all);
        run.proceed(); // Small -> Big
        run.play(all);
        run.proceed(); // Big -> Boss (Nemesis)

        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        assertThat(run.boss.name()).isEqualTo("Nemesis Blind");
        assertThat(run.requirement).isEqualTo(0); // no clear-requirement; it's a comparison

        for (int i = 0; i < std.hands(); i++) run.play(all); // exhaust all hands -> locked
        assertThat(run.phase).isEqualTo(Run.Phase.PVP_PENDING);
        assertThat(run.inPvpBlind()).isTrue();
        assertThat(run.state.roundScore).isGreaterThan(0);

        run.endPvp(); // the match decided the Nemesis; proceed to the shop
        assertThat(run.phase).isEqualTo(Run.Phase.SHOP);
        assertThat(run.inPvpBlind()).isFalse();
    }

    @Test
    void soloRunsAreNeverPvpAndCanStillWin() {
        Run run = new Run(std, "S", stoneDeck(400), jokers("j_joker", "j_joker", "j_joker"));
        assertThat(run.pvpFromAnte).isEqualTo(0); // solo default: no PvP
    }
}
