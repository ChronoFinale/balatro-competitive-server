package com.balatro.engine.exec;

/**
 * One applied {@link Command}, for the uniform action trace — the scoring side already emits a per-step
 * {@code ReplayEntry}; this is the same idea for action effects (consumable/boss/tag mutations).
 *
 * <p>It carries the STRUCTURED command, not a pre-formatted string: the engine emits data, never English.
 * "$+4" / "destroyed the 3♠" is presentation — derived by the client (with localization) from the command's
 * typed fields ({@code Command.Money(ADD, 4)}, {@code Command.DestroyCards([3♠])}). Same discipline as
 * {@code ClientView}: the server is authoritative over <i>what happened</i>; the client decides how to say it.
 */
public record TraceEntry(Command command) {}
