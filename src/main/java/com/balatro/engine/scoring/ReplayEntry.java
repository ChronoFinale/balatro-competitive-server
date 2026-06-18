package com.balatro.engine.scoring;

/**
 * One presentation event in the scoring replay log (spec §3/§5). The server
 * computes the score and emits this stream; the client only animates it and
 * never computes anything. {@code runningChips}/{@code runningMult} are the
 * totals immediately after this step, so the client can display the count-up.
 */
public record ReplayEntry(
        String source,   // what caused it: card or joker name
        String kind,     // "chips" | "mult" | "xmult" | "dollars" | "retrigger" | "info"
        String text,     // human-facing label, e.g. "+4 Mult"
        long runningChips,
        double runningMult) {
}
