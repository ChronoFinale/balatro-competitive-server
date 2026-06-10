package com.balatromp.engine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide unique id source for game entities — cards, and later jokers and
 * consumables. Mirrors Balatro's global {@code sort_id} counter
 * ({@code card.lua}: {@code G.sort_id = G.sort_id + 1; self.sort_id = G.sort_id}).
 *
 * <p>Why: effects target <em>specific</em> entities — a Tarot mutating or
 * destroying chosen cards, a sold joker, a created card — and that targeting must
 * survive the client/server boundary and the replay log. A stable unique id is
 * the robust, generic handle for "this exact card/joker", far better than fragile
 * positional indices that shift as the hand changes. Ids are unique for an
 * object's lifetime and surfaced on the wire (see {@code CardView.uid}).
 */
public final class Ids {

    private static final AtomicLong COUNTER = new AtomicLong(1);

    private Ids() {}

    public static long next() {
        return COUNTER.getAndIncrement();
    }
}
