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
 * The interesting case is the generative decomposition: the verbs become an ordered {@code List<Effect>}
 * (Destroy → Create → AddCards → AdjustMoney), not a fused Generate composite.
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
    void generativeVerbsBecomeAnOrderedEffectList() {
        // Wraith: create a Rare Joker AND set money to 0 -> [Create, AdjustMoney(SET, 0)].
        Consumable wraith = Consumables.spectral("c_w", "W").desc("d").createJoker("Rare").setMoney(0).build();
        assertThat(wraith.effects()).hasSize(2);
        Effect.Create create = (Effect.Create) wraith.effects().get(0);
        assertThat(create.spec().kind()).isEqualTo(CreateSpec.Kind.JOKER);
        assertThat(create.spec().rarity()).isEqualTo("Rare");
        Effect.AdjustMoney money = (Effect.AdjustMoney) wraith.effects().get(1);
        assertThat(money.op()).isEqualTo(Effect.Operation.SET);
        assertThat(com.balatro.engine.eval.ValueResolver.resolve(money.amount(), null)).isZero(); // Const(0) resolves without a run
    }

    @Test
    void destroyAndAddBecomeSeparateEffects() {
        // Familiar: destroy 1 in hand, add 3 face cards -> [Destroy(RandomInHand 1), AddCards(FACE, 3)].
        Consumable familiar = Consumables.spectral("c_f", "F").desc("d").destroyInHand(1).addFaceCards(3).build();
        assertThat(familiar.effects()).hasSize(2);
        Effect.Destroy destroy = (Effect.Destroy) familiar.effects().get(0);
        assertThat(destroy.selector()).isInstanceOf(com.balatro.engine.joker.def.Selector.RandomInHand.class);
        Effect.AddCards add = (Effect.AddCards) familiar.effects().get(1);
        assertThat(add.rankClass()).isEqualTo(Effect.AddCards.RankClass.FACE);
        assertThat(add.count()).isEqualTo(3);
    }

    @Test
    void anEffectlessConsumableFailsLoud() {
        assertThatThrownBy(() -> Consumables.tarot("c_void", "Void").desc("nothing").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no effect");
    }
}
