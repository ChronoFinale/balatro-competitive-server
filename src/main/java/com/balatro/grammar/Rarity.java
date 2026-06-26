package com.balatro.grammar;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Joker rarity — a closed grammar set replacing the old free-text rarity. The {@link #wire()} label is the
 * proper-case form ("Common"…) used as the keyed-RNG pool key and the client display, exposed so the JSON
 * and the RNG streams stay byte-identical to the old string. {@code wire()} is a stored-data accessor (not
 * decision logic), so the grammar stays interpretation-free.
 */
public enum Rarity {
    COMMON("Common"), UNCOMMON("Uncommon"), RARE("Rare"), LEGENDARY("Legendary");

    private final String wire;

    Rarity(String wire) { this.wire = wire; }

    /** The proper-case wire/key form ("Common") — the RNG pool key and client label. */
    @JsonValue
    public String wire() { return wire; }

    @JsonCreator
    public static Rarity fromWire(String s) {
        for (Rarity r : values()) if (r.wire.equals(s)) return r;
        throw new IllegalArgumentException("unknown rarity: " + s);
    }
}
