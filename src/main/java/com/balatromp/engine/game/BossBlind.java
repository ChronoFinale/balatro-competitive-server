package com.balatromp.engine.game;

import com.balatromp.engine.card.Suit;

/**
 * A boss blind as DATA. Effects are expressed as parameters so adding a boss is
 * a catalog row, not engine code (the configurable approach):
 *
 * <ul>
 *   <li>{@code reqMult} — score requirement = get_blind_amount(ante) × reqMult.
 *   <li>{@code handsOverride}/{@code discardsOverride} — -1 = use ruleset default.
 *   <li>{@code handSizeDelta} — e.g. -1 (The Manacle).
 *   <li>{@code debuffSuit} — that suit's cards don't score (The Club/Goad/...).
 *   <li>{@code debuffFaces} — face cards don't score (The Plant).
 * </ul>
 *
 * {@code finisher} marks the ante-8 "showdown" bosses. More exotic effects
 * (no repeat hands, must-play-5, disable a joker) are future effect kinds.
 */
public record BossBlind(
        String key,
        String name,
        String effect,
        int minAnte,
        boolean finisher,
        double reqMult,
        int reward,
        int handsOverride,
        int discardsOverride,
        int handSizeDelta,
        Suit debuffSuit,
        boolean debuffFaces,
        boolean halveBase) {

    /** Back-compat: bosses with no score-modifying ability (halveBase defaults false). */
    public BossBlind(String key, String name, String effect, int minAnte, boolean finisher,
            double reqMult, int reward, int handsOverride, int discardsOverride, int handSizeDelta,
            Suit debuffSuit, boolean debuffFaces) {
        this(key, name, effect, minAnte, finisher, reqMult, reward, handsOverride, discardsOverride,
                handSizeDelta, debuffSuit, debuffFaces, false);
    }
}
