package com.balatro.engine.state;

/**
 * The Nemesis as a first-class noun — the opponent's state a PvP joker reads, collapsing the scattered
 * {@code oppLives}/{@code oppHandsLeft}/… ints that used to live loose on {@link RunState}. Set by the
 * {@code Match} layer each sync; jokers read it through the {@code OPP_*} {@code Value.Var}s:
 *
 * <ul>
 *   <li>{@code lives} — Defensive Joker (+chips per life you're behind)</li>
 *   <li>{@code handsLeft} — Conjoined (xMult scaled by the Nemesis's remaining hands)</li>
 *   <li>{@code cardsSold} — Taxes (since the last PvP blind)</li>
 *   <li>{@code blindsSkipped} — Skip-Off (+hand/discard per blind you skipped beyond them)</li>
 *   <li>{@code shopSpentLastAnte} — Penny Pincher</li>
 * </ul>
 *
 * <p>Mutable on purpose — it's a live mirror the Match refreshes, not authored content. In solo play it
 * stays all-zero. Your own side of the match is the rest of {@link RunState} (money/jokers/hand/lives).
 */
public final class Opponent {
    public int lives = 0;
    public int handsLeft = 0;
    public int cardsSold = 0;
    public int blindsSkipped = 0;
    public int shopSpentLastAnte = 0;
}
