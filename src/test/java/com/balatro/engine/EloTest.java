package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.balatro.engine.rank.Elo;
import org.junit.jupiter.api.Test;

/**
 * Elowen (the BMP ranked formula, ported verbatim) — mirrors the reference {@code elowen.test.ts}: core Elo
 * behaviour at settled volatility, the fresh-player 1.75× swing, volatility increment/cap, and clamping.
 */
class EloTest {

    private static Elo.Rating settled(double mmr) {
        return new Elo.Rating(mmr, Elo.MAX_VOLATILITY); // gMultiplier 1.0
    }

    private static Elo.Rating fresh(double mmr) {
        return new Elo.Rating(mmr, 0); // gMultiplier 1.75
    }

    @Test
    void evenMatchSwingsByBaseChange() {
        assertThat(Elo.apply(settled(1000), settled(1000)).change()).isCloseTo(17.5, offset(0.1));
    }

    @Test
    void underdogWinPaysMoreThanEven() {
        double change = Elo.apply(settled(800), settled(1200)).change(); // 800 beats 1200
        assertThat(change).isGreaterThan(17.5).isLessThan(35).isCloseTo(23.9, offset(0.1));
    }

    @Test
    void favouriteWinPaysLessThanEven() {
        double change = Elo.apply(settled(1200), settled(800)).change(); // 1200 beats 800
        assertThat(change).isLessThan(17.5).isCloseTo(11.1, offset(0.1));
    }

    @Test
    void isZeroSum() {
        Elo.Result r = Elo.apply(settled(1000), settled(1050));
        assertThat(r.winner().mmr() - 1000).isCloseTo(1050 - r.loser().mmr(), offset(0.1));
    }

    @Test
    void freshPlayerSwings175xHarder() {
        double freshSwing = Elo.apply(fresh(1000), fresh(1000)).change();
        double settledSwing = Elo.apply(settled(1000), settled(1000)).change();
        assertThat(freshSwing).isCloseTo(settledSwing * 1.75, offset(0.1)).isCloseTo(30.6, offset(0.1));
    }

    @Test
    void volatilityIncrementsAndCaps() {
        Elo.Result r = Elo.apply(fresh(1000), new Elo.Rating(1000, 14));
        assertThat(r.winner().volatility()).isEqualTo(1);
        assertThat(r.loser().volatility()).isEqualTo(15); // 14 -> 15, capped
        assertThat(Elo.apply(settled(1000), settled(1000)).winner().volatility()).isEqualTo(15);
    }

    @Test
    void neverDropsALoserBelowZero() {
        Elo.Result r = Elo.apply(fresh(10), fresh(10)); // even fresh -> ~30.6 swing; loser would go negative
        assertThat(r.loser().mmr()).isEqualTo(0);
        assertThat(r.winner().mmr()).isCloseTo(40.6, offset(0.1));
    }
}
