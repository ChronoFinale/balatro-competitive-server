package com.balatro.engine.consumable;

import com.balatro.engine.joker.def.Effect;
import java.util.List;

/**
 * A Tarot / Spectral / Planet as data. Its {@link Effect} list is interpreted server-side against the
 * player's selected cards (resolved by unique id) by {@code Run}'s action interpreter — so a consumable can
 * mutate, destroy, or create cards, move money, or edition a joker. It is authored as a {@code List<Effect>}
 * exactly like a joker rule: the same one closed {@link Effect} vocabulary, with a {@link
 * com.balatro.engine.joker.def.Selector} naming which cards/jokers each effect targets.
 */
public record Consumable(String key, String name, String description, ConsumableType type,
                         int maxTargets, List<Effect> effects) {

    public Consumable {
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
}
