package com.balatromp.engine.card;

/**
 * A permanent mutation to a single {@link Card} — the data form of the
 * {@code MUTATE_CARD} effect (Balatro's {@code card:set_ability}/{@code set_base}/
 * {@code perma_bonus} changes). The joker- and consumable-facing writer for the
 * per-card state the engine scores: permanent chip/mult bonuses, enhancement,
 * seal, edition, rank, and suit.
 *
 * <p>Used by Hiker (perma chips), Midas Mask (faces → Gold), Vampire (strip
 * enhancement), Strength (rank +1), the suit Tarots (set suit), the enhance
 * Tarots, etc. All mutations are server-applied to authoritative card objects,
 * which keep their identity (uid) across the change.
 */
public record CardMod(Action action, int amount, Enhancement enhancement, Seal seal,
                      Edition edition, Suit suit) {

    public enum Action {
        ADD_PERMA_CHIPS, ADD_PERMA_MULT, SET_ENHANCEMENT, REMOVE_ENHANCEMENT, SET_SEAL, SET_EDITION,
        INC_RANK, SET_SUIT
    }

    public static CardMod addChips(int n) {
        return new CardMod(Action.ADD_PERMA_CHIPS, n, null, null, null, null);
    }

    public static CardMod addMult(int n) {
        return new CardMod(Action.ADD_PERMA_MULT, n, null, null, null, null);
    }

    public static CardMod setEnhancement(Enhancement e) {
        return new CardMod(Action.SET_ENHANCEMENT, 0, e, null, null, null);
    }

    public static CardMod removeEnhancement() {
        return new CardMod(Action.REMOVE_ENHANCEMENT, 0, null, null, null, null);
    }

    public static CardMod setSeal(Seal s) {
        return new CardMod(Action.SET_SEAL, 0, null, s, null, null);
    }

    public static CardMod setEdition(Edition ed) {
        return new CardMod(Action.SET_EDITION, 0, null, null, ed, null);
    }

    /** Increase rank by {@code n} steps (Strength; wraps Ace → Two). */
    public static CardMod incRank(int n) {
        return new CardMod(Action.INC_RANK, n, null, null, null, null);
    }

    /** Convert the card to a suit (the suit Tarots). */
    public static CardMod setSuit(Suit s) {
        return new CardMod(Action.SET_SUIT, 0, null, null, null, s);
    }

    /** Apply this mutation to a card (server-authoritative; mutates in place). */
    public void applyTo(Card c) {
        switch (action) {
            case ADD_PERMA_CHIPS -> c.permaChips += amount;
            case ADD_PERMA_MULT -> c.permaMult += amount;
            case SET_ENHANCEMENT -> c.enhancement = enhancement;
            case REMOVE_ENHANCEMENT -> c.enhancement = Enhancement.NONE;
            case SET_SEAL -> c.seal = seal;
            case SET_EDITION -> c.edition = edition;
            case INC_RANK -> c.rank = c.rank.shifted(amount);
            case SET_SUIT -> c.suit = suit;
        }
    }
}
