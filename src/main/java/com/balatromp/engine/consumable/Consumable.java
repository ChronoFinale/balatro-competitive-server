package com.balatromp.engine.consumable;

import com.balatromp.engine.card.CardMod;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.joker.def.CreateSpec;

/**
 * A Tarot / Spectral / Planet as data. Its {@link Effect} is interpreted
 * server-side against the player's selected cards (resolved by unique id), so a
 * consumable can mutate, destroy, or create cards — the data form of CREATE /
 * MUTATE_CARD / DESTROY for the consumable use-flow. Reuses {@link CardMod} for
 * the per-card mutation, exactly like the joker {@code MUTATE_CARD} op.
 */
public record Consumable(String key, String name, String description, ConsumableType type,
                         int maxTargets, Effect effect) {

    public sealed interface Effect
            permits Enhance, Destroy, Create, LevelAllHands, JokerEdition, Generate {}

    /** Apply a card mutation to each selected target (enhance/convert/seal/edition). */
    public record Enhance(CardMod mod) implements Effect {}

    /** Destroy each selected target (removed from the deck permanently). */
    public record Destroy() implements Effect {}

    /** Add {@code count} new cards (random numbered rank/suit) with an enhancement to the deck. */
    public record Create(int count, Enhancement enhancement) implements Effect {}

    /** Level up every poker hand by 1 (Black Hole). */
    public record LevelAllHands() implements Effect {}

    /**
     * Add an edition to a random owned joker. {@code edition == NONE} means "a random
     * Foil/Holo/Poly" (The Wheel of Fortune). {@code chanceDenominator} gates the whole
     * effect (Wheel = 4 → 1-in-4; others = 1 → always). {@code handSizeDelta} and
     * {@code destroyOtherJokers} carry the side effects of Ectoplasm (-1 hand size)
     * and Hex (destroy all other jokers).
     */
    public record JokerEdition(Edition edition, int chanceDenominator,
                               int handSizeDelta, boolean destroyOtherJokers) implements Effect {}

    /**
     * The "generative" consumables (Emperor, High Priestess, Judgement, The Soul,
     * Wraith, Hermit, Temperance, Immolate, Familiar, Grim). Applied in this order,
     * each part optional (null / 0 = skip):
     * <ol>
     *   <li>{@code destroyRandomInHand} — destroy N random hand cards (Familiar/Grim/Immolate).
     *   <li>{@code create} — spawn consumables / jokers / cards via {@link CreateSpec}.
     *   <li>{@code add} — add cards of a rank class with a (possibly random) enhancement.
     *   <li>{@code money} — a money operation.
     * </ol>
     */
    public record Generate(CreateSpec create, int destroyRandomInHand,
                           AddCards add, MoneyOp money) implements Effect {

        /** Add {@code count} cards of {@code rankClass}; {@code enhancement == null} = random per card. */
        public record AddCards(RankClass rankClass, int count, Enhancement enhancement) {
            public enum RankClass { FACE, ACE, NUMBER, ANY }
        }

        /** Double current money (capped), gain total joker sell value (capped), gain a flat
         *  amount, or set money to a fixed value. {@code amount} is the cap / delta / target. */
        public record MoneyOp(Kind kind, int amount) {
            public enum Kind { DOUBLE_CAP, SELL_VALUE_CAP, FLAT, SET }
        }
    }
}
