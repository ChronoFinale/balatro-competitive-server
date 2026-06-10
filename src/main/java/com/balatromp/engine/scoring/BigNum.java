package com.balatromp.engine.scoring;

/**
 * A break_infinity-style big number for Cryptid-scale scoring: value =
 * {@code sign · mantissa · 10^exponent}, mantissa in [1,10), exponent a
 * {@code double}. This holds numbers up to ~10^(1.8·10^308) — vastly beyond a
 * plain {@code double}'s ~1.8·10^308 ceiling — so exponential/hyper-scaling
 * effects (Cryptid {@code e_mult}, big antes) don't overflow.
 *
 * <p>Why this exists: chips × mult can grow without bound once jokers multiply
 * and exponentiate; {@code double} silently becomes {@code Infinity}. This type
 * is the universal scoring number (chips, mult, score) and supports the three
 * operations scoring needs — add, multiply, and power — plus ordered compare and
 * human notation. (Tetration-scale / OmegaNum layering is a future extension; the
 * exponent-as-double form already covers exponential growth enormously.)
 *
 * <p>Immutable. Optimized for non-negative scoring values; signed ops are
 * supported but addition of opposite signs falls back to the larger magnitude
 * when the other is negligible (the break_infinity convention).
 */
public final class BigNum implements Comparable<BigNum> {

    public static final BigNum ZERO = new BigNum(0, 0, 0);
    public static final BigNum ONE = new BigNum(1, 1, 0);

    /** Magnitudes more than this many orders apart: the smaller is negligible in +/-. */
    private static final double NEGLIGIBLE = 15.0;

    private final int sign;       // -1, 0, +1
    private final double mant;    // in [1,10) when sign != 0, else 0
    private final double exp;     // power of ten

    private BigNum(int sign, double mant, double exp) {
        this.sign = sign;
        this.mant = mant;
        this.exp = exp;
    }

    public static BigNum of(double value) {
        if (value == 0 || Double.isNaN(value)) return ZERO;
        int s = value < 0 ? -1 : 1;
        double a = Math.abs(value);
        if (Double.isInfinite(a)) return new BigNum(s, 1, Double.MAX_VALUE);
        double e = Math.floor(Math.log10(a));
        double m = a / Math.pow(10, e);
        return normalized(s, m, e);
    }

    /** Construct {@code sign · mantissa · 10^exponent} (mantissa need not be normalized). */
    public static BigNum of(int sign, double mantissa, double exponent) {
        if (sign == 0 || mantissa == 0) return ZERO;
        return normalized(sign < 0 ? -1 : 1, Math.abs(mantissa), exponent);
    }

    private static BigNum normalized(int sign, double mant, double exp) {
        if (mant == 0) return ZERO;
        double e = exp + Math.floor(Math.log10(mant));
        double m = mant / Math.pow(10, Math.floor(Math.log10(mant)));
        // guard tiny FP drift out of [1,10)
        if (m >= 10) { m /= 10; e += 1; }
        if (m < 1) { m *= 10; e -= 1; }
        return new BigNum(sign, m, e);
    }

    public BigNum add(BigNum o) {
        if (sign == 0) return o;
        if (o.sign == 0) return this;
        BigNum big = this.compareMagnitude(o) >= 0 ? this : o;
        BigNum small = big == this ? o : this;
        if (big.exp - small.exp > NEGLIGIBLE) {
            return big; // small disappears at this scale
        }
        double scaled = small.mant * Math.pow(10, small.exp - big.exp);
        double m = big.mant + (big.sign == small.sign ? scaled : -scaled);
        if (m == 0) return ZERO;
        return normalized(m < 0 ? -big.sign : big.sign, Math.abs(m), big.exp);
    }

    public BigNum multiply(BigNum o) {
        if (sign == 0 || o.sign == 0) return ZERO;
        return normalized(sign * o.sign, mant * o.mant, exp + o.exp);
    }

    public BigNum multiply(double d) {
        return multiply(of(d));
    }

    /** this^p (this must be {@code >= 0}). Handles exponents far beyond double range. */
    public BigNum pow(double p) {
        if (sign == 0) return p == 0 ? ONE : ZERO;
        if (p == 0) return ONE;
        double log10 = exp + Math.log10(mant); // log10(value)
        double r = p * log10;                  // log10(result)
        double re = Math.floor(r);
        double rm = Math.pow(10, r - re);
        return normalized(1, rm, re);
    }

    public int compareTo(BigNum o) {
        if (sign != o.sign) return Integer.compare(sign, o.sign);
        if (sign == 0) return 0;
        return sign * compareMagnitude(o);
    }

    private int compareMagnitude(BigNum o) {
        if (exp != o.exp) return Double.compare(exp, o.exp);
        return Double.compare(mant, o.mant);
    }

    /** Best-effort double (may be {@code Infinity} for values beyond ~1.8e308). */
    public double doubleValue() {
        if (sign == 0) return 0;
        return sign * mant * Math.pow(10, exp);
    }

    @Override
    public String toString() {
        if (sign == 0) return "0";
        String s = sign < 0 ? "-" : "";
        if (exp < 6) {
            double v = mant * Math.pow(10, exp);
            return s + (v == Math.rint(v) ? Long.toString((long) v) : String.format(java.util.Locale.ROOT, "%.2f", v));
        }
        String m = String.format(java.util.Locale.ROOT, "%.2f", mant);
        if (exp < 1e9) return s + m + "e" + (long) exp;          // 1.50e308
        return s + m + "e" + String.format(java.util.Locale.ROOT, "%.2e", exp); // 1.50e1.20e9 (huge)
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BigNum b)) return false;
        return sign == b.sign && mant == b.mant && exp == b.exp;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(sign, mant, exp);
    }
}
