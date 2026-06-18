package com.balatro.engine.card;

/**
 * A playing card. Identity equality (default Object) is intentional: two Kings
 * of Hearts are distinct cards in the deck. Scoring-time flags (debuffed,
 * destroyed) are transient state set by the engine during evaluation.
 */
public final class Card {

    /**
     * Unique per-card id — a {@link java.util.UUID} ({@link com.balatro.engine.Ids}). Stable for the
     * card's lifetime and surfaced to the client so effects can target this exact card (a Tarot's
     * selected cards, a destroyed card) without positional indices. A {@link #copy()} is a distinct card
     * and gets a fresh id. It's only ever a target handle, never an input to scoring/ordering/RNG.
     */
    public final java.util.UUID uid = com.balatro.engine.Ids.next();

    // Rank/suit are mutable: effects (Strength, the suit Tarots, Death) change them
    // in place, keeping the card's identity (uid) — exactly like Balatro's set_base.
    public Rank rank;
    public Suit suit;
    public Enhancement enhancement;
    public Edition edition;
    public Seal seal;

    // Permanent per-card bonuses accumulated by jokers/effects (Balatro's
    // card.ability.perma_bonus): e.g. Hiker adds chips to a card forever, other
    // effects add permanent mult. These ride with the card (deck-persistent).
    public int permaChips = 0;
    public double permaMult = 0;

    // Transient per-evaluation flags.
    public boolean debuffed = false;
    public boolean destroyed = false;

    // Boss-blind hidden information (The House/Wheel/Mark/Fish): while face down, the server still
    // knows the card and it scores normally, but ClientView must NOT reveal its identity — you play
    // it blind. Purely a hidden-information property of a card sitting in hand; reassigned on each deal.
    public boolean faceDown = false;

    // The Pillar: cards that have already been played at any point this ante are debuffed. Set when the
    // card is scored in a hand, reset at each ante boundary. Rides with the deck-persistent card.
    public boolean playedThisAnte = false;

    // Cerulean Bell: this held card is force-selected — every played hand must include it, and it can't
    // be discarded. Surfaced to the client so it renders the card as locked-in.
    public boolean forcedSelected = false;

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

    /** A Wild card counts as every suit simultaneously (Stone, which has no suit, never does). */
    public boolean isWild() {
        return enhancement == Enhancement.WILD;
    }

    /** Stone cards have no rank/suit, so they never match suit-based checks; a Wild card matches them all. */
    public boolean isSuit(Suit s) {
        return !isStone() && (isWild() || suit == s);
    }

    public boolean isFace() {
        return !isStone() && rank.isFace();
    }

    public Card copy() {
        Card c = new Card(rank, suit, enhancement, edition, seal);
        c.permaChips = permaChips;
        c.permaMult = permaMult;
        return c;
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
