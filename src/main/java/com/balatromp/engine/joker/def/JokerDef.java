package com.balatromp.engine.joker.def;

import com.balatromp.engine.joker.JokerInfo;
import java.util.List;

/**
 * A fully data-driven joker definition — pure data that {@link DataJoker} turns
 * into a live joker. This is the source-of-truth the builder UI produces and the
 * server validates and persists: you define a joker (metadata + state mutations +
 * scoring rules) without writing any code, and it flows through the same
 * authoritative pipeline as the hand-coded set.
 *
 * <p>{@code spriteUrl}/{@code spriteUrl2x} point at uploaded custom art (1x/2x);
 * when both are null the client falls back to the Balatro atlas cell
 * ({@code atlasX}/{@code atlasY}) if the local atlas is present. No art is shipped
 * with the server.
 */
public record JokerDef(
        String key,
        String name,
        String description,
        String rarity,
        int cost,
        int atlasX,
        int atlasY,
        String spriteUrl,
        String spriteUrl2x,
        boolean blueprintCompatible,
        List<Mutation> mutations,
        List<Rule> rules) {

    public JokerDef {
        mutations = mutations == null ? List.of() : List.copyOf(mutations);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public JokerInfo info() {
        return new JokerInfo(key, name, description, rarity, cost, atlasX, atlasY);
    }
}
