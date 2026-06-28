package com.balatro.engine.state;

/**
 * A competitive match's gamemode — the rules that frame two players' same-seed race. The config that used to be
 * hardcoded in {@code Match} (starting lives, the ante PvP starts at) lives here as named, explicit data, so a
 * match is parameterized by a {@code Gamemode} rather than magic constants.
 *
 * <p>Today only {@link #ATTRITION} (lives-based elimination: lose a life on a failed blind or a lost Nemesis
 * blind; out at 0 lives). Adding a mode (Showdown / Survival) is a new constant here with its config — but the
 * mode-specific <b>resolution</b> (how a Nemesis blind is decided, in {@code Match.resolveNemesisIfDecided})
 * is still Attrition-shaped; generalizing it into a per-mode strategy is the next step, intentionally deferred
 * until a second mode's actual rules are designed (no speculative engine for a single mode).
 */
public enum Gamemode {
    /** Attrition: 4 lives, Nemesis (PvP) boss blinds from ante 2, the lower score loses a life. */
    ATTRITION("Attrition", 4, 2);

    private final String display;
    private final int startingLives;
    private final int pvpFromAnte;

    Gamemode(String display, int startingLives, int pvpFromAnte) {
        this.display = display;
        this.startingLives = startingLives;
        this.pvpFromAnte = pvpFromAnte;
    }

    public String display() { return display; }

    /** Lives each player starts with; reaching 0 loses the match. */
    public int startingLives() { return startingLives; }

    /** Boss blinds at or after this ante are Nemesis (PvP) blinds (0 = never). */
    public int pvpFromAnte() { return pvpFromAnte; }
}
