package com.balatromp.engine.hand;

import java.util.Collection;

/**
 * The active set of global hand modifiers for an evaluation, flattened to booleans
 * so {@link HandEvaluator} stays branch-cheap. Built from the {@link HandMod}s
 * granted by the player's owned jokers.
 */
public record HandMods(boolean fourFingers, boolean shortcut, boolean smeared,
                       boolean pareidolia, boolean splash) {

    public static final HandMods NONE = new HandMods(false, false, false, false, false);

    public static HandMods from(Collection<HandMod> mods) {
        if (mods == null || mods.isEmpty()) return NONE;
        return new HandMods(mods.contains(HandMod.FOUR_FINGERS),
                mods.contains(HandMod.SHORTCUT), mods.contains(HandMod.SMEARED),
                mods.contains(HandMod.PAREIDOLIA), mods.contains(HandMod.SPLASH));
    }

    /** Cards needed for a Flush/Straight (Four Fingers drops it to 4). */
    public int runLength() {
        return fourFingers ? 4 : 5;
    }

    /** Max gap between consecutive straight ranks (Shortcut allows a gap of one). */
    public int maxGap() {
        return shortcut ? 2 : 1;
    }
}
