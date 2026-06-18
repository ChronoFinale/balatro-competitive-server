package com.balatro.engine.rng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
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

    /**
     * Rewind every queue whose key starts with {@code prefix}. Used to reset the
     * PvP queues ("pvp:&lt;ante&gt;:…") at the start of each hand in a PvP blind, so
     * two equal hands score the same procs regardless of hands-left.
     */
    public void reset(String prefix) {
        queues.forEach((k, q) -> {
            if (k.startsWith(prefix)) q.reset();
        });
    }

    // ------------------------------------------------------------------
    // Declarative API: resolve an RngSource against an RngContext, then draw.
    // This is the single home of all key construction — pvp routing, ante
    // scoping, and order-stripping that used to be hand-built at each call-site.
    // ------------------------------------------------------------------

    /**
     * Turn a {@link RngSource} + {@link RngContext} into the concrete stream key.
     * The one place that knows the keying rules:
     * <ul>
     *   <li>temporal scope → "" (game-long under The Order), ":ante", or ":ante:blind";</li>
     *   <li>a PER_HAND pvp source inside a PvP blind → "pvp:&lt;ante&gt;:" prefix
     *       (which {@link #reset(String) reset("pvp:")} rewinds each hand).</li>
     * </ul>
     */
    public String resolve(RngSource src, RngContext ctx) {
        String temporal = switch (src.scope()) {
            // The Order keeps GAME_LONG keys ante-free (one sequence per game); a casual
            // ruleset with orderOn=false falls back to vanilla per-ante keying.
            case GAME_LONG -> ctx.orderOn() ? "" : ":" + ctx.ante();
            case PER_ANTE -> ":" + ctx.ante();
            case PER_BLIND -> ":" + ctx.ante() + ":" + ctx.blind();
        };
        String key = src.name() + temporal;
        if (src.pvp() == RngSource.PvpMode.PER_HAND && ctx.inPvpBlind()) {
            key = "pvp:" + ctx.ante() + ":" + key;
        }
        return key;
    }

    /** The queue for {@code src} resolved against {@code ctx}. */
    public <T> GameQueue<T> queue(RngSource src, RngContext ctx, Function<Rng, T> drawOne) {
        return queue(resolve(src, ctx), drawOne);
    }

    /**
     * The queue for a <b>context-free</b> source — one that is game-long and has no PvP variant, so
     * its key is simply its name. Shop pools, packs, vouchers and tags use this: they share one
     * sequence per game regardless of ante/blind. PvP or temporally-scoped sources must use the
     * {@link #queue(RngSource, RngContext, Function) context overload} (enforced here).
     */
    public <T> GameQueue<T> queue(RngSource src, Function<Rng, T> drawOne) {
        if (src.scope() != RngSource.Scope.GAME_LONG || src.pvp() != RngSource.PvpMode.NONE) {
            throw new IllegalArgumentException("source '" + src.name() + "' is scoped/pvp; pass an RngContext");
        }
        return queue(src.name(), drawOne);
    }

    /** Consume and return the next item from {@code src}'s resolved queue. */
    public <T> T next(RngSource src, RngContext ctx, Function<Rng, T> drawOne) {
        return queue(src, ctx, drawOne).next();
    }

    /** A roll in [0,1) from {@code src}'s resolved queue (the common probability case). */
    public double roll(RngSource src, RngContext ctx) {
        return queue(src, ctx, Rng::nextDouble).next();
    }

    /**
     * Order {@code list} in place. For a {@link RngSource.Selection#COMPOSITION} source under The
     * Order this is the low-sensitivity shuffle: each element's sort value is derived from its
     * <i>identity group</i> (via {@code group}) and the run seed — never its position or draw
     * history — so equal compositions order identically and adding/removing one element leaves the
     * rest's relative order intact. Otherwise falls back to a position-sensitive Fisher–Yates on the
     * resolved stream. {@code quality} breaks ties between same-group elements (highest first) so the
     * order is stable across players that hold the same cards in a different sequence.
     */
    public <T> void shuffle(List<T> list, RngSource src, RngContext ctx,
                            Function<T, String> group, Comparator<T> quality) {
        if (ctx.orderOn() && src.selection() == RngSource.Selection.COMPOSITION) {
            compositionOrder(list, resolve(src, ctx), group, quality);
        } else {
            rng.shuffle(list, resolve(src, ctx));
        }
    }

    /**
     * Pick one element. For a {@link RngSource.Selection#COMPOSITION} source under The Order the
     * choice is by <i>identity</i> (the highest composition shuffle value), so reordering a board of
     * jokers can't change which one a "random joker" effect targets. Otherwise a position-indexed
     * draw from the resolved queue. Returns null for an empty list.
     */
    public <T> T pick(List<T> items, RngSource src, RngContext ctx,
                      Function<T, String> group, Comparator<T> quality) {
        if (items.isEmpty()) {
            return null;
        }
        if (ctx.orderOn() && src.selection() == RngSource.Selection.COMPOSITION) {
            List<T> copy = new ArrayList<>(items);
            compositionOrder(copy, resolve(src, ctx), group, quality);
            return copy.get(0);
        }
        int idx = (int) (roll(src, ctx) * items.size()) % items.size();
        return items.get(idx);
    }

    /**
     * Assign each element a shuffle value from a per-identity-group stream and sort the list by it
     * (descending). Idempotent: a {@link RandomStreams#freshStream fresh} stream per (key, group)
     * means recomputing yields the same values. Same-group elements are ordered by {@code quality}
     * first, then drawn successively from that group's stream — so duplicates get distinct values.
     */
    private <T> void compositionOrder(List<T> list, String key,
                                      Function<T, String> group, Comparator<T> quality) {
        Map<String, List<T>> groups = new HashMap<>();
        for (T item : list) {
            groups.computeIfAbsent(group.apply(item), k -> new ArrayList<>()).add(item);
        }
        Map<T, Double> shuffleVal = new IdentityHashMap<>();
        for (Map.Entry<String, List<T>> e : groups.entrySet()) {
            List<T> g = e.getValue();
            g.sort(quality);
            Rng gr = rng.freshStream(key + "|" + e.getKey());
            for (T item : g) {
                shuffleVal.put(item, gr.nextDouble());
            }
        }
        list.sort((a, b) -> Double.compare(shuffleVal.get(b), shuffleVal.get(a)));
    }
}
