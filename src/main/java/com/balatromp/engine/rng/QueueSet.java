package com.balatromp.engine.rng;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The bundle of game-long {@link GameQueue}s for one run, all derived from the
 * run's master seed. This is the run-scoped home of BMP-style queue determinism:
 * each named category (joker pool, planets, tarots, packs, vouchers, soul/black
 * hole, glass breaks, lucky, bloodstone, …) is one persistent queue with its own
 * cursor that advances as that category is consumed — replacing per-event keyed
 * draws so behavior matches BMP's shared-sequence model.
 *
 * <p>Both players build a QueueSet from the same seed, so every queue is
 * identical between them; their cursors move independently. A queue is created
 * lazily on first {@link #queue} call with that key; the draw function defines
 * how a raw item is produced from the category's own RNG stream.
 */
public final class QueueSet {

    private final RandomStreams rng;
    private final Map<String, GameQueue<?>> queues = new HashMap<>();

    public QueueSet(RandomStreams rng) {
        this.rng = rng;
    }

    /**
     * The queue for {@code key}, created on first use. {@code drawOne} produces
     * the next raw item from the category's dedicated RNG stream; it is only
     * consulted when the queue is first created (within a run a category's draw
     * rule is constant).
     */
    @SuppressWarnings("unchecked")
    public <T> GameQueue<T> queue(String key, Function<Rng, T> drawOne) {
        return (GameQueue<T>) queues.computeIfAbsent(key, k -> {
            Rng stream = rng.stream("queue:" + k);
            return new GameQueue<T>(() -> drawOne.apply(stream));
        });
    }
}
