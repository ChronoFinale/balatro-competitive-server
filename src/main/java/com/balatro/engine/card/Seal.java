package com.balatro.engine.card;

public enum Seal {
    NONE,
    RED,     // retrigger the card once (ScoringEngine)
    BLUE,    // held at end of round: creates the Planet for the round's last hand (Run.applyBlueSeals)
    GOLD,    // +$3 when scored (ScoringEngine)
    PURPLE;  // creates a random Tarot when discarded (Run.applyPurpleSeals)
}
