package com.balatromp.engine.net;

import com.balatromp.engine.card.Card;

/**
 * A card as the client is allowed to see it (for rendering). Used only for cards
 * the player legitimately sees — their own hand. The deck order is never
 * projected into views (hidden-information boundary, spec §8).
 */
public record CardView(long uid, String rank, String suit, String enhancement, String edition, String seal) {

    public static CardView of(Card c) {
        return new CardView(c.uid, c.rank.name(), c.suit.name(),
                c.enhancement.name(), c.edition.name(), c.seal.name());
    }
}
