package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Aspect;
import com.balatro.engine.game.Blinds.BlindType;
import com.balatro.engine.game.Bosses;
import com.balatro.engine.game.DeckCatalog;
import com.balatro.engine.game.Modify;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Ruleset;
import com.balatro.engine.state.Stake;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hand size is one game {@link Aspect}, and a deck, a joker and a boss all modify it through the same
 * {@link Modify} shape — no per-source field bags. A Painted Deck (+2), a Juggler (+1) and the Manacle
 * (−1) compose into one folded value, exactly as a {@code HandMod} composes hand-evaluation rules.
 */
class AspectModifierTest {

    @Test
    void foldResolvesSetThenAddThenMultiply() {
        // SET replaces the base (a boss override beats the ruleset default), then ADDs, then MULTIPLYs.
        List<Modify> mods = List.of(
                Modify.add(Aspect.HANDS, 3),       // Burglar
                Modify.set(Aspect.HANDS, 1),       // Needle (override)
                Modify.multiply(Aspect.HANDS, 2)); // hypothetical doubler
        assertThat(Modify.fold(4, Aspect.HANDS, mods)).isEqualTo((1 + 3) * 2);
    }

    @Test
    void deckJokerAndBossAllModifyTheSameHandSizeAspect() {
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
}
