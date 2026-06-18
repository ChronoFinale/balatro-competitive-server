package com.balatro.engine.card;

public enum Seal {
    NONE,
    RED,     // retrigger the card once
    BLUE,    // creates a Planet card (out of scope for the slice)
    GOLD,    // +$3 when scored (out of scope for the slice)
    PURPLE;  // creates a Tarot on discard (out of scope for the slice)
}
