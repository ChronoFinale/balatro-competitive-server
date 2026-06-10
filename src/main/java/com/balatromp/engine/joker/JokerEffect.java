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
    /** Multiplies current chips (Balatro {@code x_chips}); null = no effect (distinct from x1.0). */
    public Double xChips;
    public Double xMult;
    /** Raise the running mult to this power (exponential / Cryptid {@code e_mult}-style); null = none. */
    public Double powMult;
    public long dollars;
    public int repetitions;
    public double hMult;
    /** A nested effect applied immediately after this one's scoring fields (SMODS {@code extra} chain). */
    public JokerEffect extra;
    /** A permanent mutation to apply to the relevant card (MUTATE_CARD: Hiker/Midas/Vampire). */
    public com.balatromp.engine.card.CardMod cardMod;
    /** A card to create server-side (CREATE: 8 Ball/Cartomancer); applied only on a real play. */
    public com.balatromp.engine.joker.def.CreateSpec create;
    /** Swap the running chips and mult (Balatro {@code swap}). */
    public boolean swap;
    /** Balance chips and mult (Balatro {@code balance}); semantics TBD — no content uses it yet. */
    public boolean balance;
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

    public static JokerEffect xChips(double x) {
        JokerEffect e = new JokerEffect();
        e.xChips = x;
        return e;
    }

    /** Attach a nested effect applied right after this one (the {@code extra} chain). */
    public JokerEffect andThen(JokerEffect next) {
        this.extra = next;
        return this;
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
