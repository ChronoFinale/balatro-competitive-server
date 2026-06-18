package com.balatro.engine.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The big-number scoring type: add/multiply/power across magnitudes that overflow
 * a double, ordered compare, and readable notation — the foundation for
 * Cryptid-scale / high-ante scoring without silent Infinity.
 */
class BigNumTest {

    @Test
    void smallValuesAreExactLikeDoubles() {
        assertThat(BigNum.of(1234).doubleValue()).isEqualTo(1234.0);
        assertThat(BigNum.of(5).multiply(BigNum.of(6)).doubleValue()).isEqualTo(30.0);
        assertThat(BigNum.of(300).add(BigNum.of(800)).doubleValue()).isEqualTo(1100.0);
        // hybrid: in-range integer math is EXACT (no base-10 normalization drift)
        assertThat(BigNum.of(3).multiply(BigNum.of(4)).doubleValue()).isEqualTo(12.0);
        assertThat(BigNum.of(2).pow(10).doubleValue()).isEqualTo(1024.0);
    }

    @Test
    void addAcrossHugeMagnitudesIsScaleAware() {
        // same scale: real sum
        assertThat(BigNum.of(1e300).add(BigNum.of(1e300)).doubleValue()).isEqualTo(2e300);
        // tiny addend vanishes against an astronomically larger one (break_infinity convention)
        BigNum huge = BigNum.of(10).pow(400);
        assertThat(huge.add(BigNum.of(1234))).isEqualTo(huge);
    }

    @Test
    void powerGoesWellBeyondDoubleRange() {
        BigNum tenTo400 = BigNum.of(10).pow(400); // 10^400 — a plain double is Infinity here
        assertThat(Double.isInfinite(tenTo400.doubleValue())).isTrue();
        assertThat(tenTo400.toString()).isEqualTo("1.00e400"); // but BigNum holds it exactly
        // exponential blow-up: (10^9)^50 = 10^450
        assertThat(BigNum.of(1e9).pow(50).toString()).isEqualTo("1.00e450");
    }

    @Test
    void ordersCorrectlyAcrossScales() {
        assertThat(BigNum.of(1e308).compareTo(BigNum.of(1e307))).isPositive();
        assertThat(BigNum.of(10).pow(500).compareTo(BigNum.of(10).pow(499))).isPositive();
        assertThat(BigNum.of(42).compareTo(BigNum.of(42))).isZero();
        assertThat(BigNum.of(5).compareTo(BigNum.of(9))).isNegative();
    }

    @Test
    void notationIsReadable() {
        assertThat(BigNum.of(999).toString()).isEqualTo("999");
        assertThat(BigNum.of(1.5e308).toString()).isEqualTo("1.50e308");
        assertThat(BigNum.ZERO.toString()).isEqualTo("0");
    }

    @Test
    void chipsTimesMultStyleComposition() {
        // chips=500, mult grows ×3 ×4 then ^2 -> score = 500 * ((1*3*4)^2) = 500*144 = 72000
        BigNum mult = BigNum.ONE.multiply(3).multiply(4).pow(2);
        BigNum score = BigNum.of(500).multiply(mult);
        assertThat(score.doubleValue()).isEqualTo(72000.0); // in-range -> exact
    }
}
