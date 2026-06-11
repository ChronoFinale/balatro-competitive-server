package com.balatromp.engine.rng;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A Balatro-Multiplayer–style <b>game-long queue</b>: a single deterministic,
 * lazily-generated sequence with a per-player cursor. This is the determinism
 * <i>shape</i> that makes a 1v1 fair — both players, seeded identically, get the
 * exact same sequence, and each advances their own cursor by their own actions
 * (rerolling, opening packs, triggering a joker). Timing and ordering can't
 * desync them: the Nth item is always the same item.
 *
 * <p>Generation is sequential and cached, so item <i>i</i> is stable once drawn.
 * {@link #nextWhere} models BMP's "block/skip" rule: an item that can't appear
 * (a Planet for an unlocked hand, an already-owned Voucher, a blocked Joker) is
 * consumed and the queue advances to the next acceptable item — never re-rolled.
 *
 * @param <T> the queued item (a joker key, a planet, a hit/miss flag, …)
 */
public final class GameQueue<T> {

    private final Supplier<T> draw;
    private final List<T> items = new ArrayList<>();
    private int cursor = 0;

    public GameQueue(Supplier<T> draw) {
        this.draw = draw;
    }

    private T at(int i) {
        while (items.size() <= i) {
            items.add(draw.get());
        }
        return items.get(i);
    }

    /** The next item without consuming it. */
    public T peek() {
        return at(cursor);
    }

    /** Consume and return the next item. */
    public T next() {
        return at(cursor++);
    }

    /**
     * Consume items until one is acceptable, advancing past every skipped item
     * (BMP's block/unlock rule). Returns the first accepted item.
     */
    public T nextWhere(Predicate<? super T> accept) {
        T v;
        do {
            v = at(cursor++);
        } while (!accept.test(v));
        return v;
    }

    /** Reveal {@code n} acceptable items in order (skipping past blocked ones). */
    public List<T> take(int n, Predicate<? super T> accept) {
        List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(nextWhere(accept));
        }
        return out;
    }

    /** Reveal {@code n} items in order (no skipping). */
    public List<T> take(int n) {
        List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(next());
        }
        return out;
    }

    /** How many items have been consumed so far (this player's position). */
    public int cursor() {
        return cursor;
    }

    /**
     * Rewind the cursor to the start (cached items are kept, so the same sequence
     * replays). Used by PvP queues, which reset each hand so equal hands get equal
     * procs regardless of how many hands are left.
     */
    public void reset() {
        cursor = 0;
    }
}
