package com.balatromp.engine.state;

/**
 * A competitive ruleset as DATA (not a Lua mod). This is the "flexibility to
 * create simple rulesets for ranked" surface: starting params, scaling, win
 * condition, and the blind-requirement curve are all config. Expand by adding
 * fields (banned content, modifiers, deck type) — never by forking the engine.
 *
 * {@code blindBaseAmounts} are the ante 1..8 base chip requirements (vanilla
 * values); antes beyond 8 use the formula in {@link com.balatromp.engine.game.Blinds}.
 * {@code winAnte} = beat this ante's boss to win the run (0 = endless/survival).
 */
public record Ruleset(
        String name,
        int startingMoney,
        int hands,
        int discards,
        int handSize,
        double anteScaling,
        int winAnte,
        int[] blindBaseAmounts) {

    public static Ruleset standard() {
        return new Ruleset("Standard", 4, 4, 3, 8, 1.0, 8,
                new int[]{300, 800, 2000, 5000, 11000, 20000, 35000, 50000});
    }
}
