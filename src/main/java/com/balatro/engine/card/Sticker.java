package com.balatro.engine.card;

import com.balatro.grammar.Effect;
import com.balatro.grammar.Value;
import java.util.List;
import java.util.Locale;

/**
 * A Sticker is the joker-attached member of the <b>Modifier-on-object</b> family — the sibling of
 * {@link Enhancement} / {@link Edition} / {@link Seal} on cards. A first-class Balatro noun (you see it on
 * the joker) whose behaviour is grammar, not a bespoke flag:
 *
 * <ul>
 *   <li><b>Eternal</b> — can't be sold or destroyed: a structural protection read at the sell/destroy sites.</li>
 *   <li><b>Perishable</b> — debuffed after {@link #PERISHABLE_ROUNDS} rounds: a per-joker countdown Counter.</li>
 *   <li><b>Rental</b> — costs {@link #RENTAL_RATE} per round: expressed AS DATA, an {@link Effect.AdjustMoney}
 *       each end of round, exactly like a money joker.</li>
 * </ul>
 *
 * <p>These were stake flags ({@code eternalsInShop}) + magic-string joker state ({@code "rental"}) with the
 * behaviour scattered in {@code Run}. Now the sticker is the type, and what it does is the shared vocabulary.
 */
public enum Sticker {
    ETERNAL,
    PERISHABLE,
    RENTAL;

    /** Rounds a Perishable joker survives before it's debuffed (game.lua:1914). */
    public static final int PERISHABLE_ROUNDS = 5;
    /** Rent a Rental joker charges each round. */
    public static final int RENTAL_RATE = 3;
    /** Per-slot chance a stake rolls each sticker onto a shop joker. */
    public static final double STICKER_CHANCE = 0.30;

    /** The joker-state flag key this sticker is stored under. */
    public String flag() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** This sticker's end-of-round behaviour, AS DATA. Rental is a money cost (an AdjustMoney, consistent
     *  with every other money effect); Eternal/Perishable have no end-of-round effect of this shape. */
    public List<Effect> endOfRound() {
        return this == RENTAL
                ? List.of(new Effect.AdjustMoney(Effect.Operation.SUBTRACT, new Value.Const(RENTAL_RATE)))
                : List.of();
    }
}
