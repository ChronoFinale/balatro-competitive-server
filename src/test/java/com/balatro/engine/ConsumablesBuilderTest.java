package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.consumable.Consumable;
import com.balatro.engine.consumable.ConsumableType;
import com.balatro.dsl.Consumables;
import com.balatro.engine.joker.def.CreateSpec;
import com.balatro.engine.joker.def.Effect;
import org.junit.jupiter.api.Test;

/**
 * The fluent {@link Consumables} builder produces the same records the catalog used to write by hand.
 * The interesting case is the generative folding: several verbs accumulate into one {@link Effect.Generate}.
 */
class ConsumablesBuilderTest {

    @Test
    void directEffectsBuildTheRightRecord() {
        Consumable magician = Consumables.tarot("c_x", "X").desc("d").targets(2)
                .enhance(CardMod.setEnhancement(Enhancement.LUCKY)).build();
        assertThat(magician.type()).isEqualTo(ConsumableType.TAROT);
        assertThat(magician.maxTargets()).isEqualTo(2);
        assertThat(magician.effects()).singleElement().isInstanceOf(Effect.MutateCard.class);
    }

    @Test
    void generativeVerbsFoldIntoOneGenerate() {
        // Wraith: create a Rare Joker AND set money to 0 -> a single Generate carrying both parts.
        Consumable wraith = Consumables.spectral("c_w", "W").desc("d").createJoker("Rare").setMoney(0).build();
        Effect.Generate g = (Effect.Generate) wraith.effects().get(0);
        assertThat(g.create().kind()).isEqualTo(CreateSpec.Kind.JOKER);
        assertThat(g.create().rarity()).isEqualTo("Rare");
        assertThat(g.money().kind()).isEqualTo(Effect.Generate.MoneyOp.Kind.SET);
        assertThat(g.money().amount()).isZero();
        assertThat(g.destroyRandomInHand()).isZero();
        assertThat(g.add()).isNull();
    }

    @Test
    void destroyAndAddFoldTogether() {
        // Familiar: destroy 1 in hand, add 3 face cards.
        Consumable familiar = Consumables.spectral("c_f", "F").desc("d").destroyInHand(1).addFaceCards(3).build();
        Effect.Generate g = (Effect.Generate) familiar.effects().get(0);
        assertThat(g.destroyRandomInHand()).isEqualTo(1);
        assertThat(g.add().rankClass()).isEqualTo(Effect.Generate.AddCards.RankClass.FACE);
        assertThat(g.add().count()).isEqualTo(3);
    }

    @Test
    void anEffectlessConsumableFailsLoud() {
        assertThatThrownBy(() -> Consumables.tarot("c_void", "Void").desc("nothing").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no effect");
    }
}
