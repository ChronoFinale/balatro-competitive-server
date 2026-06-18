package com.balatro.engine.consumable;

/** The three consumable families. */
public enum ConsumableType {
    TAROT, PLANET, SPECTRAL;

    /** Proper-case label used for joker triggers (e.g. Constellation counts "Planet"). */
    public String label() {
        return switch (this) {
            case TAROT -> "Tarot";
            case PLANET -> "Planet";
            case SPECTRAL -> "Spectral";
        };
    }
}
