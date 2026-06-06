package com.balatromp.engine.joker;

/**
 * The mutation a joker (or card) contributes — the return-field set from spec §3,
 * unified across contexts. The ScoringEngine applies present fields in a fixed
 * order: chips -> mult -> dollars -> hMult -> xMult (matching evaluate_play).
 *
 * <p>{@code xMult} is boxed so {@code null} means "no multiplicative effect"
 * (distinct from x1.0).
 */
public final class JokerEffect {

    public long chips;
    public double mult;
    public Double xMult;
    public long dollars;
    public int repetitions;
    public double hMult;
    public String message;
    /** Attribution for the replay log; set by the engine/Blueprint. */
    public String source;

    public static JokerEffect chips(long c) {
        JokerEffect e = new JokerEffect();
        e.chips = c;
        return e;
    }

    public static JokerEffect mult(double m) {
        JokerEffect e = new JokerEffect();
        e.mult = m;
        return e;
    }

    public static JokerEffect xMult(double x) {
        JokerEffect e = new JokerEffect();
        e.xMult = x;
        return e;
    }

    public static JokerEffect repetitions(int n) {
        JokerEffect e = new JokerEffect();
        e.repetitions = n;
        return e;
    }

    public static JokerEffect dollars(long d) {
        JokerEffect e = new JokerEffect();
        e.dollars = d;
        return e;
    }

    public JokerEffect msg(String m) {
        this.message = m;
        return this;
    }
}
