package com.balatro.engine.joker;

/**
 * Display + shop metadata for a joker. Logic lives in the {@link Joker}; this is
 * what the client renders. Descriptions are our own concise wording (the
 * mechanic is fact; the game's exact prose is not copied). {@code atlasX/atlasY}
 * are the sprite cell in Balatro's Jokers atlas (from game.lua) — used only if
 * the local {@code web-assets} atlas is present; no art is shipped.
 *
 * rarity: "Common" | "Uncommon" | "Rare".
 */
public record JokerInfo(
        String key,
        String name,
        String description,
        String rarity,
        int cost,
        int atlasX,
        int atlasY) {
}
