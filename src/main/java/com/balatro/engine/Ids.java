package com.balatro.engine;

import java.util.UUID;

/**
 * Unique id source for game entities — cards, and later jokers and consumables.
 *
 * <p>An id is a {@link UUID}: globally unique, no coordination, no sequence. All it has to be is
 * <em>unique</em> — a stable handle so effects can target <em>this exact</em> entity (a Tarot's chosen
 * cards, a destroyed card, a sold joker) across the client/server boundary and through save/restore,
 * without fragile positional indices. It is deliberately <b>not</b> derived from the seed:
 *
 * <ul>
 *   <li><b>Outcome-neutral.</b> The id is only ever a target handle — never an input to scoring, ordering,
 *       or RNG (the Idol &amp; friends match on rank/suit + a seed-derived target, never on uid). So it
 *       cannot affect a game result.</li>
 *   <li><b>No consistency needed.</b> Save/restore is "snapshot the state, reload it, keep going" — ids
 *       are read back verbatim, never re-derived, and a random UUID can never collide with a restored one.</li>
 * </ul>
 */
public final class Ids {

    private Ids() {}

    /** A fresh globally-unique id. */
    public static UUID next() {
        return UUID.randomUUID();
    }
}
