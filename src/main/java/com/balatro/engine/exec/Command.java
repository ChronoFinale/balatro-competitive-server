package com.balatro.engine.exec;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.CardMod;
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
 * engine applies the Command via {@code Run.apply}. This is the action-side twin of the scoring
 * {@code JokerEffect}: one resolved-mutation path the interpreter switches funnel through.
 *
 * <p>Migration in progress (review plan #1): action-effect families move from direct mutation in {@code Run}
 * onto this hierarchy one at a time. Cases are added as families are migrated.
 */
public sealed interface Command {

    /** Adjust the run's money by an already-resolved {@code amount}; the verb carries the direction. */
    record Money(Effect.Operation op, double amount) implements Command {}

    /** Destroy these specific cards (already selected) from the deck + hand (Hanged Man, Immolate, Sixth Sense). */
    record DestroyCards(List<Card> cards) implements Command {}

    /** Apply a {@link CardMod} (enhance / convert suit / seal / edition) to these specific cards (Magician,
     *  Empress, Star, Aura, the seal tarots). */
    record MutateCards(List<Card> cards, CardMod mod) implements Command {}

    /** Add a CreateSpec's contents to the run (consumables/jokers/playing cards). */
    record Create(CreateSpec spec) implements Command {}

    /** Add these already-built playing cards to the deck + hand (Incantation, Familiar, Grim). */
    record AddCardsToDeck(List<Card> cards) implements Command {}

    /** Overwrite {@code target}'s attributes with {@code source}'s (Death: the left card becomes the right). */
    record OverwriteCard(Card target, Card source) implements Command {}

    /** Give a specific joker an edition (Ectoplasm/Hex, the bound joker). */
    record EditionJoker(Joker target, Edition edition) implements Command {}

    /** Add a copy of {@code source}'s joker, up to the slot limit (Ankh, Invisible Joker); the copy carries
     *  the active {@code variant} (MP reworks) and no edition. */
    record CopyJoker(Joker source, String variant) implements Command {}

    /** Destroy every owned joker except {@code keep} (Ankh/Hex's wipe). */
    record DestroyOtherJokers(Joker keep) implements Command {}

    /** Add a copy of a consumable by {@code key} (The Fool, Perkeo); {@code ignoreSlotCap} for Perkeo's Negative. */
    record CopyConsumable(String key, boolean ignoreSlotCap) implements Command {}

    /** Adjust the hand size by a delta (Ectoplasm -1, Juggle +3). */
    record HandSize(int delta) implements Command {}

    /** Level a poker hand by {@code levels} (negative = delevel); {@code hand} = the resolved target. */
    record LevelHand(HandType hand, int levels) implements Command {}
}
