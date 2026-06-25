package com.balatro.engine.exec;

/**
 * One applied {@link Command}, for the uniform action trace. The scoring side already emits a per-step
 * {@code ReplayEntry}; this is the same idea for action effects (consumable/boss/tag mutations), so a
 * Hanged Man destroying 3♠,5♦ or The Tooth taking $4 shows up in an inspectable stream just like a joker's
 * "+4 Mult". {@code kind} groups it ("money"/"destroy"/"create"/…); {@code detail} is the human label.
 */
public record TraceEntry(String source, String kind, String detail) {
    public TraceEntry(String kind, String detail) { this("", kind, detail); }
}
