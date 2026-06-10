package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.state.Ruleset;
import org.junit.jupiter.api.Test;

/**
 * Tarots/Spectrals act on cards by unique id: enhance selected cards, destroy
 * selected cards (removed from the deck), and create new cards. Targeting uses
 * the stable card id, not positional indices.
 */
class ConsumableTest {

    private Run freshRun() {
        return new Run(Ruleset.standard(), "CONSUME"); // starts in ante-1 small with a drawn hand
    }

    @Test
    void magicianEnhancesSelectedCardsByUid() {
        Run run = freshRun();
        Card a = run.state.hand.get(0);
        Card b = run.state.hand.get(1);
        run.state.consumables.add("c_magician");
        assertThat(run.useConsumable(0, new long[]{a.uid, b.uid})).isNull();
        assertThat(a.enhancement).isEqualTo(Enhancement.LUCKY);
        assertThat(b.enhancement).isEqualTo(Enhancement.LUCKY);
        assertThat(run.state.consumables).isEmpty(); // consumed
    }

    @Test
    void hangedManDestroysSelectedCards() {
        Run run = freshRun();
        int before = run.state.hand.size();
        Card a = run.state.hand.get(0);
        Card b = run.state.hand.get(1);
        run.state.consumables.add("c_hanged_man");
        assertThat(run.useConsumable(0, new long[]{a.uid, b.uid})).isNull();
        assertThat(run.state.hand).doesNotContain(a, b);
        assertThat(run.state.hand).hasSize(before - 2);
    }

    @Test
    void tooManyTargetsRejected() {
        Run run = freshRun();
        long[] three = {run.state.hand.get(0).uid, run.state.hand.get(1).uid, run.state.hand.get(2).uid};
        run.state.consumables.add("c_magician"); // max 2
        assertThat(run.useConsumable(0, three)).isEqualTo("too many targets");
        assertThat(run.state.consumables).hasSize(1); // not consumed on rejection
    }

    @Test
    void incantationCreatesBonusCards() {
        Run run = freshRun();
        int before = run.state.hand.size();
        run.state.consumables.add("c_incantation");
        assertThat(run.useConsumable(0, new long[0])).isNull();
        assertThat(run.state.hand).hasSize(before + 4);
        long bonus = run.state.hand.stream().filter(c -> c.enhancement == Enhancement.BONUS).count();
        assertThat(bonus).isGreaterThanOrEqualTo(4);
        // created cards have fresh unique ids
        assertThat(run.state.hand.stream().map(c -> c.uid).distinct().count())
                .isEqualTo(run.state.hand.size());
    }
}
