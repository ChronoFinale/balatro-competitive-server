package com.balatromp.engine.rng;

/**
 * A <b>declared, composable description</b> of one source of game randomness. The
 * insight behind it: "The Order" (BMP's ranked variance-reduction mode) and a
 * joker's bespoke RNG quirk are not special code paths — they are just bundles of
 * a few independent <i>properties</i> on a draw. Bloodstone is simply a game-long
 * probability source that <em>also</em> has a per-hand PvP variant; the deck deal
 * is simply a per-blind source whose selection is composition-ordered. Encoding
 * those as properties (instead of hand-built key strings scattered across the
 * engine) means {@link QueueSet#resolve} is the single place that turns intent
 * into a concrete keyed stream, and adding a new mechanic is a declaration, not a
 * new branch.
 *
 * <p>Immutable; build by composition, e.g.
 * {@code RngSource.of("lucky_mult").pvpPerHand()} or
 * {@code RngSource.of("deal").perBlind().composition()}.
 *
 * @param name      the base category key (e.g. "lucky_mult", "deal", "vouchers")
 * @param scope     temporal keying — see {@link Scope}
 * @param pvp       whether a PvP blind switches to a per-hand-resetting variant
 * @param selection how an item/order is chosen from the stream — see {@link Selection}
 */
public record RngSource(String name, Scope scope, PvpMode pvp, Selection selection) {

    /** How much of the run's progress is folded into the stream key. */
    public enum Scope {
        /** One sequence for the whole game (BMP "The Order" default — ante is NOT in the key). */
        GAME_LONG,
        /** A fresh sequence per ante (e.g. the boss roll, which must vary each ante). */
        PER_ANTE,
        /** A fresh sequence per (ante, blind) (e.g. the deck deal, re-shuffled each blind). */
        PER_BLIND
    }

    /** Behavior inside a PvP (Nemesis) blind. */
    public enum PvpMode {
        /** Always the game-long stream. */
        NONE,
        /** In a PvP blind, route to a "pvp:&lt;ante&gt;:…" variant that the Run rewinds each hand,
         *  so two equal hands proc equally regardless of how many hands are left. */
        PER_HAND
    }

    /** How an outcome is picked from the source. */
    public enum Selection {
        /** Draw the next item from the game-long queue (the default). */
        SEQUENTIAL,
        /** Composition-ordered: each candidate gets a shuffle value derived from its <i>identity</i>
         *  (not its list position or draw history), so equal board/deck states behave identically and
         *  a one-card difference perturbs only that card. Used for the deck shuffle and "random
         *  joker/card" picks — BMP's biggest variance reducer. */
        COMPOSITION,
        /** Weighted by how many copies of an identity are present (Idol/Mail-style). */
        WEIGHTED_COUNT
    }

    /** A bare game-long, sequential source. */
    public static RngSource of(String name) {
        return new RngSource(name, Scope.GAME_LONG, PvpMode.NONE, Selection.SEQUENTIAL);
    }

    public RngSource perAnte() {
        return new RngSource(name, Scope.PER_ANTE, pvp, selection);
    }

    public RngSource perBlind() {
        return new RngSource(name, Scope.PER_BLIND, pvp, selection);
    }

    public RngSource pvpPerHand() {
        return new RngSource(name, scope, PvpMode.PER_HAND, selection);
    }

    public RngSource composition() {
        return new RngSource(name, scope, pvp, Selection.COMPOSITION);
    }

    public RngSource weightedCount() {
        return new RngSource(name, scope, pvp, Selection.WEIGHTED_COUNT);
    }

    /** A sibling source with a sub-keyed name ({@code name:suffix}), same properties. Used where one
     *  category fans out into many keys (per-joker probability, per-consumable picks). */
    public RngSource sub(String suffix) {
        return new RngSource(name + ":" + suffix, scope, pvp, selection);
    }
}
