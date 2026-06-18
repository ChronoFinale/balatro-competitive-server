package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Blinds.BlindType;
import com.balatro.engine.game.Bosses;
import com.balatro.engine.game.DeckCatalog;
import com.balatro.engine.joker.def.Modify;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.joker.def.Value;
import com.balatro.engine.state.Ruleset;
import com.balatro.engine.state.Stake;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A {@link Modify} is the write half of the same vocabulary conditions read: it targets a
 * {@link Value.Var}, not a parallel "aspect". Hand size is just a game variable, and a deck, a joker
 * and a boss all modify it through the same shape — folded once.
 */
class GameVarModifierTest {

    @Test
    void foldResolvesSetThenAddThenMultiply() {
        // SET replaces the base (a boss override beats the ruleset default), then ADDs, then MULTIPLYs.
        List<Modify> mods = List.of(
                Modify.add(Value.Var.HANDS_LEFT, 3),       // Burglar
                Modify.set(Value.Var.HANDS_LEFT, 1),       // Needle (override)
                Modify.multiply(Value.Var.HANDS_LEFT, 2)); // hypothetical doubler
        assertThat(Modify.fold(4, Value.Var.HANDS_LEFT, mods)).isEqualTo((1 + 3) * 2);
    }

    @Test
    void deckJokerAndBossAllModifyTheSameHandSizeVariable() {
        // Painted Deck (+2 hand size), Juggler (+1); the j_jokers are just there to clear the blinds.
        Run run = new Run(Ruleset.standard(), "HS", stoneDeck(300),
                jokers("j_juggler", "j_joker", "j_joker", "j_joker"),
                Stake.WHITE, DeckCatalog.get("d_painted"));
        run.forcedBoss = Bosses.of("bl_manacle", "The Manacle").desc("-1 hand size").handSize(-1).build();

        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Small
        run.proceed();
        assertThat(run.state.handSize).isEqualTo(8 + 2 + 1); // base + Painted + Juggler (no boss yet)

        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Big
        run.proceed();
        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        assertThat(run.state.handSize).isEqualTo(8 + 2 + 1 - 1); // ...the Manacle joins the same fold
    }

    @Test
    void everySourceFoldsTogetherOnTheSameVariables() {
        // The whole point: a deck, a boss, jokers and a voucher all modify the same game variables
        // through one fold. Painted (+2 size), Juggler (+1 size), Grabber (+1 hand), Burglar (+3 hands,
        // no discards), forced Needle (set hands 1). Hands: set 1 then +3 (Burglar) +1 (Grabber) = 5.
        Run run = new Run(Ruleset.standard(), "ALL", stoneDeck(300),
                jokers("j_juggler", "j_burglar", "j_joker", "j_joker", "j_joker"),
                Stake.WHITE, DeckCatalog.get("d_painted"));
        run.state.vouchers.add("v_grabber");
        run.forcedBoss = Bosses.of("bl_needle", "The Needle").desc("one hand")
                .requirement(100).hands(1).build();

        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Small
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Big
        run.proceed();
        assertThat(run.blind).isEqualTo(BlindType.BOSS);

        assertThat(run.state.handsLeft).isEqualTo(1 + 3 + 1);        // Needle SET 1, then Burglar +3, Grabber +1
        assertThat(run.state.discardsLeft).isZero();                 // Burglar: no discards (final override)
        assertThat(run.state.handSize).isEqualTo(8 + 2 + 1);         // base + Painted + Juggler
    }

    @Test
    void consumableSlotsAreDerivedFromDeckAndVoucherModifiers() {
        // CONSUMABLE_SLOTS is a real read/write variable: base 2, folded with deck + voucher Modifys.
        Run nebula = new Run(Ruleset.standard(), "NB", stoneDeck(300), jokers("j_joker"),
                Stake.WHITE, DeckCatalog.get("d_nebula")); // Nebula: -1 consumable slot
        assertThat(nebula.state.consumableSlots).isEqualTo(2 - 1);

        Run magic = new Run(Ruleset.standard(), "MG", stoneDeck(300), jokers("j_joker"),
                Stake.WHITE, DeckCatalog.get("d_magic")); // Magic: starts with the Crystal Ball voucher (+1)
        assertThat(magic.state.consumableSlots).isEqualTo(2 + 1);
    }

    @Test
    void aVoucherFoldsIntoTheResourceLikeAnyOtherModifier() {
        // A voucher is no longer a key-string in Run; it carries Modify(HANDS_LEFT/DISCARDS_LEFT, ...)
        // as data and folds in with the deck/boss/joker modifiers.
        Run run = new Run(Ruleset.standard(), "VR", stoneDeck(300), jokers("j_joker", "j_joker", "j_joker"));
        run.state.vouchers.add("v_grabber");   // Permanently +1 hand
        run.state.vouchers.add("v_wasteful");  // Permanently +1 discard

        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Small
        run.proceed();                                          // -> Big; resources recomputed with the vouchers
        assertThat(run.state.handsLeft).isEqualTo(Ruleset.standard().hands() + 1);    // Grabber folded in
        assertThat(run.state.discardsLeft).isEqualTo(Ruleset.standard().discards() + 1); // Wasteful folded in
    }
}
