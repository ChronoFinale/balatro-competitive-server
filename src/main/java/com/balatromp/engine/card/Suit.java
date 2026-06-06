package com.balatromp.engine.card;

public enum Suit {
    SPADES, HEARTS, CLUBS, DIAMONDS;

    public boolean isRed() {
        return this == HEARTS || this == DIAMONDS;
    }
}
