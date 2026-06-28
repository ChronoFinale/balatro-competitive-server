package com.balatro.engine.rank;

/**
 * "Elowen" — the BMP ranked MMR update, ported verbatim from the Balatro Multiplayer server's ranked
 * formula (Botlatro-Multiplayer {@code calculateMMR.ts}, written by Owen; hence Elo + Owen). Reduced to
 * the 1v1 case. Adopting it verbatim keeps our numbers comparable to BMP's official ladder (balatromp.com).
 *
 * <p>It IS Elo at the core: {@code 1 / (1 + 10^((winner − loser)/variance))} is Elo's expected-score
 * logistic, scaled by a fixed K ({@code = 2·baseChange}) and a games-played "volatility" multiplier that
 * decays a new player's swings from 1.75× down to 1.0× over their first {@code maxVolatility} games (a
 * provisional-rating / decaying-K trick — its version of "placements"; NOT Glicko RD math despite the name).
 *
 * <p>At settled volatility (gMultiplier 1.0): an even match swings by {@code baseChange} (17.5); an underdog
 * win pays up to {@code 2·baseChange} (35); a favourite win pays toward 0. Fresh players swing 1.75× harder.
 * Pure — the caller persists.
 */
public final class Elo {

    /** Base MMR change; max swing is 2× this (35). */
    public static final double BASE_CHANGE = 17.5;
    /** Logistic scale — larger = flatter = MMR gaps matter less (BMP uses 1200, vs textbook Elo's 400). */
    public static final double VARIANCE = 1200.0;
    /** Games over which a new player's volatility climbs to settle their swing from 1.75× to 1.0×. */
    public static final int MAX_VOLATILITY = 15;

    /** A player's ladder state. */
    public record Rating(double mmr, int volatility) {}

    /** Both players' new ratings after a match, plus the (rounded) signed change (zero-sum in 1v1). */
    public record Result(Rating winner, Rating loser, double change) {}

    private Elo() {}

    /** Apply one 1v1 result: {@code winner} beat {@code loser}. */
    public static Result apply(Rating winner, Rating loser) {
        double avgVolatility = (winner.volatility() + loser.volatility()) / 2.0;
        double gMultiplier = 1.75 - avgVolatility * 0.05;

        double numerator = 2 * BASE_CHANGE;
        double exponent = (winner.mmr() - loser.mmr()) / VARIANCE;
        double denominator = 1 + Math.pow(10, exponent);
        double change = gMultiplier * (numerator / denominator); // unrounded; the mmr sum is rounded

        return new Result(
                new Rating(clamp(round1(winner.mmr() + change), 0, 9999),
                        Math.min(winner.volatility() + 1, MAX_VOLATILITY)),
                new Rating(clamp(round1(loser.mmr() - change), 0, 9999),
                        Math.min(loser.volatility() + 1, MAX_VOLATILITY)),
                round1(change));
    }

    private static double round1(double n) {
        return Math.round(n * 10) / 10.0;
    }

    private static double clamp(double n, double lo, double hi) {
        return Math.max(lo, Math.min(hi, n));
    }
}
