package com.balatro.engine.net;

import com.balatro.engine.card.Card;
import java.util.UUID;

/**
 * A card as the client is allowed to see it (for rendering). Used only for cards
 * the player legitimately sees — their own hand. The deck order is never
 * projected into views (hidden-information boundary, spec §8).
 */
public record CardView(UUID uid, String rank, String suit, String enhancement, String edition, String seal,
                       int permaChips, double permaMult, boolean faceDown, boolean forcedSelected) {

    public static CardView of(Card c) {
        // Face-down cards (boss blinds) reveal nothing but their stable uid (the render/target handle).
        // This is the hidden-information boundary doing its job: the server knows the card, the client
        // sees only a card back — so you cannot peek at what you're forced to play.
        if (c.faceDown) {
            return new CardView(c.uid, null, null, null, null, null, 0, 0, true, c.forcedSelected);
        }
        return new CardView(c.uid, c.rank.name(), c.suit.name(),
                c.enhancement.name(), c.edition.name(), c.seal.name(), c.permaChips, c.permaMult, false, c.forcedSelected);
    }
}
