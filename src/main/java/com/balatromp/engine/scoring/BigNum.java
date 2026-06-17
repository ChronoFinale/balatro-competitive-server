package com.balatromp.engine.scoring;

/**
 * A hybrid big number for Cryptid-scale scoring. While a value fits a normal
 * {@code double} it is stored and computed as one — so ordinary scoring is bit-for-bit
 * identical to plain {@code double}/{@code long} arithmetic (no base-10 normalization
 * drift). Only when an operation would overflow {@code double} (~1.8·10^308) does it
 * switch to a {@code mantissa · 10^exponent} form (exponent a {@code double}), holding
 * numbers up to ~10^(1.8·10^308). This is the Talisman/break_infinity approach: exact
 * where it matters, unbounded where it counts.
 *
 * <p>Supports the three operations scoring needs — add, multiply, power — plus ordered
 * compare and human notation. The {@code pow} path uses log/exp once big, so it is
 * approximate at astronomical scale (as all floating big-nums are; the final score is
 * floored anyway). Immutable.
 */
public final class BigNum implements Comparable<BigNum> {

    public static final BigNum ZERO = small(0);
    public static final BigNum ONE = small(1);

    /** Orders of magnitude apart beyond which the smaller addend is negligible. */
    private static final double NEGLIGIBLE = 15.0;
    /** Below this exponent a value collapses back to an exact double. */
    private static final double COLLAPSE_EXP = 300.0;

    private final boolean big;
    private final double d;      // value when !big
    private final int sign;      // when big: -1 / +1 (never 0 — zero is small)
    private final double mant;   // when big: [1,10)
    private final double exp;    // when big

    private BigNum(boolean big, double d, int sign, double mant, double exp) {
        this.big = big;
        this.d = d;
        this.sign = sign;
        this.mant = mant;
        this.exp = exp;
    }

    private static BigNum small(double v) {
        return new BigNum(false, v, 0, 0, 0);
    }

    /** Build (and normalize) a big-form value, collapsing back to a double when it fits. */
    private static BigNum bignum(int sign, double mantissa, double exponent) {
        if (sign == 0 || mantissa == 0) return ZERO;
        int s = sign < 0 ? -1 : 1;
        double m = Math.abs(mantissa);
        double e = exponent + Math.floor(Math.log10(m));
        m = m / Math.pow(10, Math.floor(Math.log10(m)));
        if (m >= 10) { m /= 10; e += 1; }
        if (m < 1) { m *= 10; e -= 1; }
        if (e < COLLAPSE_EXP) return small(s * m * Math.pow(10, e)); // fits a double exactly enough
        return new BigNum(true, 0, s, m, e);
    }

    public static BigNum of(double v) {
        if (Double.isNaN(v)) return ZERO;
        if (Double.isInfinite(v)) return new BigNum(true, 0, v < 0 ? -1 : 1, 1, Double.MAX_VALUE);
        return small(v);
    }

    /** {sign, mantissa, exponent} for either representation. */
    private double[] parts() {
        if (big) return new double[]{sign, mant, exp};
        if (d == 0) return new double[]{0, 0, 0};
        int s = d < 0 ? -1 : 1;
        double a = Math.abs(d);
        double e = Math.floor(Math.log10(a));
        return new double[]{s, a / Math.pow(10, e), e};
    }

    public BigNum add(BigNum o) {
        if (!big && !o.big) {
            double r = d + o.d;
            if (Double.isFinite(r)) return small(r);
        }
        double[] a = parts();
        double[] b = o.parts();
        if (a[0] == 0) return o;
        if (b[0] == 0) return this;
        // order by magnitude
        boolean aBigger = a[2] > b[2] || (a[2] == b[2] && a[1] >= b[1]);
        double[] hi = aBigger ? a : b;
        double[] lo = aBigger ? b : a;
        if (hi[2] - lo[2] > NEGLIGIBLE) return aBigger ? this : o;
        double scaled = lo[1] * Math.pow(10, lo[2] - hi[2]);
        double m = hi[1] + (hi[0] == lo[0] ? scaled : -scaled);
        if (m == 0) return ZERO;
        return bignum(m < 0 ? -(int) hi[0] : (int) hi[0], Math.abs(m), hi[2]);
    }

    public BigNum multiply(BigNum o) {
        if (!big && !o.big) {
            double r = d * o.d;
            if (Double.isFinite(r)) return small(r);
        }
        double[] a = parts();
        double[] b = o.parts();
        if (a[0] == 0 || b[0] == 0) return ZERO;
        return bignum((int) (a[0] * b[0]), a[1] * b[1], a[2] + b[2]);
    }

    public BigNum multiply(double factor) {
        return multiply(of(factor));
    }

    /** this^p (this must be {@code >= 0}). */
    public BigNum pow(double p) {
        if (!big) {
            double r = Math.pow(d, p);
            if (Double.isFinite(r)) return small(r);
        }
        double[] a = parts();
        if (a[0] <= 0) return p == 0 ? ONE : ZERO;
        double log10 = a[2] + Math.log10(a[1]);
        double r = p * log10;
        double re = Math.floor(r);
        return bignum(1, Math.pow(10, r - re), re);
    }

    @Override
    public int compareTo(BigNum o) {
        if (!big && !o.big) return Double.compare(d, o.d);
        double[] a = parts();
        double[] b = o.parts();
        if (a[0] != b[0]) return Double.compare(a[0], b[0]);
        if (a[0] == 0) return 0;
        int mag = a[2] != b[2] ? Double.compare(a[2], b[2]) : Double.compare(a[1], b[1]);
        return (int) a[0] * mag;
    }

    /**
     * Largest integer {@code <=} this. Exact for double-backed values; a no-op for big-form values
     * (>=10^300), which already exceed integer precision and are integral at their scale.
     */
    public BigNum floor() {
        return big ? this : small(Math.floor(d));
    }

    /** Best-effort double (may be {@code Infinity} for values beyond ~1.8e308). */
    public double doubleValue() {
        return big ? sign * mant * Math.pow(10, exp) : d;
    }

    @Override
    public String toString() {
        if (!big) {
            if (d == 0) return "0";
            double a = Math.abs(d);
            String s = d < 0 ? "-" : "";
            if (a < 1e6) return s + (a == Math.rint(a)
                    ? Long.toString((long) a) : String.format(java.util.Locale.ROOT, "%.2f", a));
            double e = Math.floor(Math.log10(a));
            return s + String.format(java.util.Locale.ROOT, "%.2f", a / Math.pow(10, e)) + "e" + (long) e;
        }
        String s = sign < 0 ? "-" : "";
        String m = String.format(java.util.Locale.ROOT, "%.2f", mant);
        if (exp < 1e9) return s + m + "e" + (long) exp;
        return s + m + "e" + String.format(java.util.Locale.ROOT, "%.2e", exp);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BigNum b)) return false;
        return compareTo(b) == 0;
    }

    @Override
    public int hashCode() {
        return big ? Double.hashCode(exp) : Double.hashCode(d);
    }
}
