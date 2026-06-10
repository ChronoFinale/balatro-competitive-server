package com.balatromp.engine.card;

/**
 * A permanent mutation to a single {@link Card} — the data form of the
 * {@code MUTATE_CARD} effect (Balatro's {@code card:set_ability}/{@code perma_bonus}
 * changes). This is the joker-facing writer for the per-card state the engine
 * already scores: permanent chip/mult bonuses, enhancement, seal, edition.
 *
 * <p>Used by Hiker (perma chips), Midas Mask (faces → Gold), Vampire (strip
 * enhancement), and the enhance-Tarots / seal-Spectrals when those land. All
 * mutations are server-applied to authoritative card objects.
 */
public record CardMod(Action action, int amount, Enhancement enhancement, Seal seal, Edition edition) {

    public enum Action {
        ADD_PERMA_CHIPS, ADD_PERMA_MULT, SET_ENHANCEMENT, REMOVE_ENHANCEMENT, SET_SEAL, SET_EDITION
    }

    public static CardMod addChips(int n) {
        return new CardMod(Action.ADD_PERMA_CHIPS, n, null, null, null);
    }

    public static CardMod addMult(int n) {
        return new CardMod(Action.ADD_PERMA_MULT, n, null, null, null);
    }

    public static CardMod setEnhancement(Enhancement e) {
        return new CardMod(Action.SET_ENHANCEMENT, 0, e, null, null);
    }

    public static CardMod removeEnhancement() {
        return new CardMod(Action.REMOVE_ENHANCEMENT, 0, null, null, null);
    }

    public static CardMod setSeal(Seal s) {
        return new CardMod(Action.SET_SEAL, 0, null, s, null);
    }

    public static CardMod setEdition(Edition ed) {
        return new CardMod(Action.SET_EDITION, 0, null, null, ed);
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
        }
    }
}
