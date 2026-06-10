package com.balatromp.engine.state;

import com.balatromp.engine.joker.JokerLibrary;
import java.util.List;

/**
 * A competitive ruleset as DATA (not a Lua mod). Together with the joker
 * definitions it names, the ruleset <em>fully dictates the match</em>: starting
 * params, scaling, win condition, the blind-requirement curve, and — via
 * {@code jokerPool} — exactly which jokers can appear in the shop. Two players on
 * the same agreed ruleset get the same content surface; the only variable is how
 * they build and play. Expand by adding fields (banned content, deck type) —
 * never by forking the engine.
 *
 * {@code blindBaseAmounts} are the ante 1..8 base chip requirements (vanilla
 * values); antes beyond 8 use the formula in {@link com.balatromp.engine.game.Blinds}.
 * {@code winAnte} = beat this ante's boss to win the run (0 = endless/survival).
 * {@code jokerPool} is the set of joker keys the shop may offer; null/empty means
 * the curated built-in set.
 */
public record Ruleset(
        String name,
        int startingMoney,
        int hands,
        int discards,
        int handSize,
        double anteScaling,
        int winAnte,
        int[] blindBaseAmounts,
        List<String> jokerPool,
        String jokerVariant,
        String deckType) {

    public Ruleset {
        jokerPool = (jokerPool == null || jokerPool.isEmpty())
                ? JokerLibrary.builtinKeys()
                : List.copyOf(jokerPool);
        // Which joker behavior-variant this match uses (e.g. "default" single-player vs
        // "multiplayer"); a joker key resolves to its matching variant def if one exists.
        jokerVariant = (jokerVariant == null || jokerVariant.isBlank()) ? "default" : jokerVariant;
        deckType = (deckType == null || deckType.isBlank()) ? "d_base" : deckType;
    }

    /** A ruleset with a joker variant and the base deck. */
    public Ruleset(String name, int startingMoney, int hands, int discards, int handSize,
                   double anteScaling, int winAnte, int[] blindBaseAmounts, List<String> jokerPool,
                   String jokerVariant) {
        this(name, startingMoney, hands, discards, handSize, anteScaling, winAnte,
                blindBaseAmounts, jokerPool, jokerVariant, "d_base");
    }

    /** A ruleset with the built-in pool and the base deck/variant. */
    public Ruleset(String name, int startingMoney, int hands, int discards, int handSize,
                   double anteScaling, int winAnte, int[] blindBaseAmounts, List<String> jokerPool) {
        this(name, startingMoney, hands, discards, handSize, anteScaling, winAnte,
                blindBaseAmounts, jokerPool, "default", "d_base");
    }

    /** Convenience: a ruleset using the curated built-in joker pool. */
    public Ruleset(String name, int startingMoney, int hands, int discards, int handSize,
                   double anteScaling, int winAnte, int[] blindBaseAmounts) {
        this(name, startingMoney, hands, discards, handSize, anteScaling, winAnte, blindBaseAmounts, null);
    }

    public static Ruleset standard() {
        return new Ruleset("Standard", 4, 4, 3, 8, 1.0, 8,
                new int[]{300, 800, 2000, 5000, 11000, 20000, 35000, 50000});
    }
}
