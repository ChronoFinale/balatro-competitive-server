package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.consumable.Consumable;
import com.balatro.engine.consumable.ConsumableType;
import com.balatro.dsl.Consumables;
import com.balatro.grammar.CreateSpec;
import com.balatro.grammar.Effect;
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
        Consumable wraith = Consumables.spectral("c_w", "W").desc("d").createJoker(com.balatro.grammar.Rarity.RARE).setMoney(0).build();
        assertThat(wraith.effects()).hasSize(2);
        Effect.Create create = (Effect.Create) wraith.effects().get(0);
        assertThat(create.spec().kind()).isEqualTo(CreateSpec.Kind.JOKER);
        assertThat(create.spec().rarity()).isEqualTo(com.balatro.grammar.Rarity.RARE);
        Effect.Write money = (Effect.Write) wraith.effects().get(1);          // money is a Modify(MONEY) write now
        assertThat(money.mod().variable()).isEqualTo(com.balatro.grammar.Value.Var.MONEY);
        assertThat(money.mod().op()).isEqualTo(Effect.Operation.SET);
        assertThat(com.balatro.engine.eval.ValueResolver.resolve(money.mod().value(), null)).isZero(); // Const(0) resolves without a run
    }

    @Test
    void destroyAndAddBecomeSeparateEffects() {
        // Familiar: destroy 1 in hand, add 3 face cards -> [Destroy(RandomInHand 1), AddCards(FACE, 3)].
        Consumable familiar = Consumables.spectral("c_f", "F").desc("d").destroyInHand(1).addFaceCards(3).build();
        assertThat(familiar.effects()).hasSize(2);
        Effect.Destroy destroy = (Effect.Destroy) familiar.effects().get(0);
        assertThat(destroy.selector()).isInstanceOf(com.balatro.grammar.Selector.RandomInHand.class);
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
