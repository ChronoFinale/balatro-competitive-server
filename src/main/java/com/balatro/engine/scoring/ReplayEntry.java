package com.balatro.engine.scoring;

/**
 * One presentation event in the scoring replay log (spec §3/§5). The server computes the score and emits this
 * stream; the client only animates it and never computes anything.
 *
 * <p>It carries STRUCTURED DATA, never engine-built English: a {@link Kind} (what kind of step) and a numeric
 * {@code amount} (its magnitude — {@code +4 Mult} is {@code (MULT, 4)}, {@code x1.5 Mult} is {@code (XMULT,
 * 1.5)}, a Foil's chips is {@code (CHIPS, 50)}). The human label ("+4 Mult", localized) is derived by the
 * client from {@code kind} + {@code amount} + {@code source}. Same discipline as {@code ClientView}: the
 * server is authoritative over <i>what happened</i>; the client decides how to say it.
 *
 * <p>{@code runningChips}/{@code runningMult} are the totals immediately after this step, so the client can
 * display the count-up.
 */
public record ReplayEntry(
        String source,   // what fired: card or joker name (an identifier the client localizes, not prose)
        Kind kind,       // the structured type of step
        double amount,   // the step's numeric magnitude (signed; 0 for non-numeric kinds)
        long runningChips,
        double runningMult) {

    /** The kind of replay step — the client renders the sign/label/suffix from this plus {@code amount}. */
    public enum Kind {
        CHIPS, MULT, XCHIPS, XMULT, POWMULT, DOLLARS, RETRIGGER, DESTROY, COPY, MUTATE, CREATE, LEVELUP, DISCARDS, INFO
    }
}
