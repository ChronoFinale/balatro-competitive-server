package com.balatro.engine.game;

/**
 * Server-authoritative competitive clock realizing the <b>"decisions, not strict time"</b> model
 * (see the {@code session-lifecycle-and-timers} design): time accrues <b>only</b> while a player is in
 * a decision window — when the server is awaiting their input — and <b>resolution/animation is free</b>
 * regardless of length. So an animation-dense build (Vagabond spawning ten tarots, big retrigger chains)
 * is never penalized: that time is the engine resolving a choice already made, not the human deciding.
 *
 * <p>A decision window opens when the server presents an input-required state and closes when the
 * player's action arrives. At the window's start a capped <i>animation credit</i> — the canonical
 * resolution time that preceded it — is granted as free time, so reacting <i>during</i> the animation
 * costs nothing. The credit is clamped to a server-canonical {@code cap}, which makes a client-driven
 * pause <b>self-limiting</b>: a client cannot claim more free time than the server already knows the
 * animation can take, so the worst case of "cheating the pause" is bounded to a couple of seconds.
 *
 * <p>Wall-clock millis are <b>injected</b> (the server stamps real time at the transport boundary) so
 * the engine stays deterministic and this is unit-testable. This is the leaf the PvP speed clock
 * (build-order step 3) accrues into; it is intentionally not yet wired into {@code Match}.
 */
public final class DecisionClock {

    private long accruedMillis;       // committed decision time across all closed windows
    private long windowStartMillis;   // wall-clock at the current window's open
    private long windowCreditMillis;  // free animation time granted to the current window (already capped)
    private boolean open;

    /**
     * Open a decision window at {@code nowMillis}. {@code requestedCreditMillis} is the
     * resolution/animation time to grant as free (the canonical replay duration, or a client-reported
     * pause); it is clamped to {@code [0, max(0, capMillis)]} so it can never exceed the
     * server-canonical maximum. Opening while already open re-opens (the prior window is discarded).
     */
    public void open(long nowMillis, long requestedCreditMillis, long capMillis) {
        windowStartMillis = nowMillis;
        windowCreditMillis = Math.max(0, Math.min(requestedCreditMillis, Math.max(0, capMillis)));
        open = true;
    }

    /** Open a window with no animation credit — a pure decision (e.g. blind select, reroll). */
    public void open(long nowMillis) {
        open(nowMillis, 0, 0);
    }

    /**
     * Close the open window (the player's action arrived): accrue {@code max(0, elapsed - credit)}.
     * No-op if no window is open, so duplicate/out-of-order closes are safe.
     */
    public void close(long nowMillis) {
        if (!open) return;
        accruedMillis += chargeable(nowMillis);
        open = false;
    }

    /** Total decision time charged so far (millis), excluding any currently-open window. */
    public long accruedMillis() {
        return accruedMillis;
    }

    /**
     * Decision time as of {@code nowMillis} <i>including</i> the currently-open window — a live readout
     * for a countdown UI. Pure: does not mutate; {@link #close} is what commits.
     */
    public long elapsedAsOf(long nowMillis) {
        return open ? accruedMillis + chargeable(nowMillis) : accruedMillis;
    }

    /** Whether a decision window is currently open (the clock is "running"). */
    public boolean isOpen() {
        return open;
    }

    /** Chargeable time for the open window as of now: elapsed past the (capped) animation credit. */
    private long chargeable(long nowMillis) {
        long elapsed = Math.max(0, nowMillis - windowStartMillis);
        return Math.max(0, elapsed - windowCreditMillis);
    }
}
