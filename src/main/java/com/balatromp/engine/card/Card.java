package com.balatromp.engine.card;

/**
 * A playing card. Identity equality (default Object) is intentional: two Kings
 * of Hearts are distinct cards in the deck. Scoring-time flags (debuffed,
 * destroyed) are transient state set by the engine during evaluation.
 */
public final class Card {

    public final Rank rank;
    public final Suit suit;
    public Enhancement enhancement;
    public Edition edition;
    public Seal seal;

    // Transient per-evaluation flags.
    public boolean debuffed = false;
    public boolean destroyed = false;

    public Card(Rank rank, Suit suit) {
        this(rank, suit, Enhancement.NONE, Edition.NONE, Seal.NONE);
    }

    public Card(Rank rank, Suit suit, Enhancement enhancement, Edition edition, Seal seal) {
        this.rank = rank;
        this.suit = suit;
        this.enhancement = enhancement;
        this.edition = edition;
        this.seal = seal;
    }

    public int id() {
        return rank.id;
    }

    /** Chips this card contributes when scored (Stone cards ignore rank). */
    public int baseChips() {
        return enhancement == Enhancement.STONE ? 0 : rank.baseChips;
    }

    public boolean isStone() {
        return enhancement == Enhancement.STONE;
    }

    /** Stone cards have no rank/suit, so they never match suit/rank-based checks. */
    public boolean isSuit(Suit s) {
        return !isStone() && suit == s;
    }

    public boolean isFace() {
        return !isStone() && rank.isFace();
    }

    public Card copy() {
        return new Card(rank, suit, enhancement, edition, seal);
    }

    @Override
    public String toString() {
        String r = switch (rank) {
            case TWO -> "2"; case THREE -> "3"; case FOUR -> "4"; case FIVE -> "5";
            case SIX -> "6"; case SEVEN -> "7"; case EIGHT -> "8"; case NINE -> "9";
            case TEN -> "T"; case JACK -> "J"; case QUEEN -> "Q"; case KING -> "K"; case ACE -> "A";
        };
        String s = switch (suit) {
            case SPADES -> "s"; case HEARTS -> "h"; case CLUBS -> "c"; case DIAMONDS -> "d";
        };
        StringBuilder sb = new StringBuilder(r).append(s);
        if (enhancement != Enhancement.NONE) sb.append('[').append(enhancement).append(']');
        if (edition != Edition.NONE) sb.append('{').append(edition).append('}');
        if (seal != Seal.NONE) sb.append('(').append(seal).append(')');
        return sb.toString();
    }
}
