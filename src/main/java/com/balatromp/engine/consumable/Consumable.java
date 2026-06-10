package com.balatromp.engine.consumable;

import com.balatromp.engine.card.CardMod;
import com.balatromp.engine.card.Enhancement;

/**
 * A Tarot / Spectral / Planet as data. Its {@link Effect} is interpreted
 * server-side against the player's selected cards (resolved by unique id), so a
 * consumable can mutate, destroy, or create cards — the data form of CREATE /
 * MUTATE_CARD / DESTROY for the consumable use-flow. Reuses {@link CardMod} for
 * the per-card mutation, exactly like the joker {@code MUTATE_CARD} op.
 */
public record Consumable(String key, String name, String description, ConsumableType type,
                         int maxTargets, Effect effect) {

    public sealed interface Effect permits Enhance, Destroy, Create {}

    /** Apply a card mutation to each selected target (enhance/convert/seal/edition). */
    public record Enhance(CardMod mod) implements Effect {}

    /** Destroy each selected target (removed from the deck permanently). */
    public record Destroy() implements Effect {}

    /** Add {@code count} new cards (random numbered rank/suit) with an enhancement to the deck. */
    public record Create(int count, Enhancement enhancement) implements Effect {}
}
