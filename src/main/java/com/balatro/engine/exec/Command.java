package com.balatro.engine.exec;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.Joker;
import com.balatro.grammar.CreateSpec;
import com.balatro.grammar.Effect;
import java.util.List;

/**
 * A CONCRETE, already-resolved mutation the engine will apply — the unified output of interpreting an action
 * effect. The {@code Effect} grammar is declarative ("destroy the random-in-hand selector"); interpreting it
 * (resolving Values, rolling RNG to pick the actual cards) yields a Command ("destroy these two cards"). The
 * engine applies the Command and writes a {@link TraceEntry}. This is the action-side twin of the scoring
 * {@code JokerEffect}: one path, one trace, replayable, previewable.
 *
 * <p>Migration in progress (review plan #1): action-effect families move from direct mutation in {@code Run}
 * onto this hierarchy one at a time. Cases are added as families are migrated.
 */
public sealed interface Command {

    /** Adjust the run's money by an already-resolved {@code amount}; the verb carries the direction. */
    record Money(Effect.Operation op, double amount) implements Command {}

    /** Destroy these specific cards (already selected) from the deck + hand (Hanged Man, Immolate, Sixth Sense). */
    record DestroyCards(List<Card> cards) implements Command {}

    /** Add a CreateSpec's contents to the run (consumables/jokers/playing cards). */
    record Create(CreateSpec spec) implements Command {}

    /** Give a specific joker an edition (Ectoplasm/Hex, the bound joker). */
    record EditionJoker(Joker target, Edition edition) implements Command {}

    /** Adjust the hand size by a delta (Ectoplasm -1, Juggle +3). */
    record HandSize(int delta) implements Command {}

    /** Level a poker hand by {@code levels} (negative = delevel); {@code hand} = the resolved target. */
    record LevelHand(HandType hand, int levels) implements Command {}
}
