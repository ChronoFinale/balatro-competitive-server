package com.balatro.grammar;

import com.balatro.engine.card.Enhancement;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A number resolved at evaluation time — the magnitude half of a data effect. PURE DATA: a {@code Value} is
 * an AST node with no behaviour; {@code com.balatro.engine.eval.ValueResolver} interprets it against a run.
 * Covers every scaling shape the game uses: a flat {@link Const} (+4 Mult); a {@link State} counter that
 * ramps over a run (Ride the Bus's streak); a {@link Count} of cards matching a predicate (+X per face card
 * held); and a {@link RunVar} reading live run state. All scaling shapes are {@code base + scale * n}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Value.Const.class, name = "const"),
    @JsonSubTypes.Type(value = Value.State.class, name = "state"),
    @JsonSubTypes.Type(value = Value.StateStep.class, name = "stateStep"),
    @JsonSubTypes.Type(value = Value.Count.class, name = "count"),
    @JsonSubTypes.Type(value = Value.RunVar.class, name = "runVar"),
    @JsonSubTypes.Type(value = Value.RunVarStep.class, name = "runVarStep"),
    @JsonSubTypes.Type(value = Value.Stat.class, name = "stat"),
    @JsonSubTypes.Type(value = Value.HeldExtreme.class, name = "heldExtreme"),
    @JsonSubTypes.Type(value = Value.DeckRankCount.class, name = "deckRankCount"),
    @JsonSubTypes.Type(value = Value.Clamp.class, name = "clamp"),
    @JsonSubTypes.Type(value = Value.Diff.class, name = "diff"),
    @JsonSubTypes.Type(value = Value.HandTypePlays.class, name = "handTypePlays"),
    @JsonSubTypes.Type(value = Value.OtherJokersSellSum.class, name = "otherJokersSellSum"),
    @JsonSubTypes.Type(value = Value.Random.class, name = "random"),
    @JsonSubTypes.Type(value = Value.Prop.class, name = "prop"),
})
public sealed interface Value {

    /** A fixed amount. */
    record Const(double amount) implements Value {}

    /** A declared constant property of the joker ({@code prop("mult")}) — a named binding, not a literal. */
    record Prop(String name) implements Value {}

    /** {@code base + scale * (server-only state variable)}. */
    record State(String var, double base, double scale) implements Value {}

    /** {@code base + scale * (sum of OTHER owned jokers' sell value, max(1, cost/2))} — Swashbuckler. */
    record OtherJokersSellSum(double base, double scale) implements Value {}

    /** {@code base + scale * (times the current poker hand has been played this run)} — Supernova. */
    record HandTypePlays(double base, double scale) implements Value {}

    /** Clamps an inner value to {@code [min, max]} — decay jokers floor at 0/1 (Ice Cream, Ramen). */
    record Clamp(Value inner, double min, double max) implements Value {}

    /** The difference of two values, {@code left − right} — one quantity defined relative to another
     *  (Skip-Off: how many blinds you've skipped beyond the Nemesis). */
    record Diff(Value left, Value right) implements Value {}

    /** {@code base + scale * floor(state / per)} — stepwise state scaling (Yorick: x1 per 23 discarded). */
    record StateStep(String var, double base, double scale, double per) implements Value {}

    /** Which set of cards a {@link Count} scans. */
    enum Source { PLAYED, SCORING, HELD, EVENT }

    /**
     * {@code base + scale * (number of cards in source matching match)}. The predicate reuses the per-card
     * {@link Condition} vocabulary, so "each face card", "each Diamond", "each Glass card" all compose.
     */
    record Count(Source source, Condition match, double base, double scale) implements Value {}

    /** Which live run-state quantity a {@link RunVar} reads. Implements {@link Property}: the run/shop/economy
     *  bag, with the hand nouns lifted out into {@link Hand}. */
    enum Var implements Property {
        // --- readable run-state quantities (a condition can read these) ---
        MONEY, CONSUMABLE_SLOTS, JOKER_SLOTS, ANTE, DISCARDS_USED,
        HANDS_PLAYED, HANDS_PLAYED_TOTAL, ROUNDS_PLAYED, CARDS_DISCARDED_TOTAL, LUCKY_TRIGGERS,
        UNIQUE_PLANETS, OBELISK_STREAK, BLINDS_SKIPPED, OPP_BLINDS_SKIPPED, OPP_LIVES_BEHIND, OPP_HANDS_LEFT, OPP_CARDS_SOLD,
        OPP_SHOP_SPENT, GLASS_MULT, BLIND_PROGRESS, TOTAL_SELL_VALUE,
        // --- derived economy/shop policy variables: written by Modifys (folded in EconomyConfig /
        //     ShopEconomy), not yet read by any condition. Reading one throws (see ValueResolver.readVar). ---
        INTEREST_CAP, MONEY_PER_HAND, MONEY_PER_DISCARD, MIN_MONEY,
        SHOP_SLOTS, PRICE_MULTIPLIER, REROLL_DISCOUNT, EDITION_MULTIPLIER, POLY_MULTIPLIER,
        TAROT_RATE, PLANET_RATE, WIN_ANTE, BOSS_REROLLS_PER_ANTE, HELD_PLANET_MULT,
        CELESTIAL_MOST_PLAYED, SHOP_PLAYING_CARD_RATE, SHOP_CARDS_ENHANCED, FREE_REROLLS, SPECTRAL_RATE,
        // The "1 in X" odds multiplier (base 1): Oops! All 6s contributes MULTIPLY 2 per copy, so the
        // fold gives 2^copies — every probability threshold is scaled by it (Run.probabilityNumerator).
        PROBABILITY_MULTIPLIER,
        // Boolean policies (fold to >= 1 when any owner grants them): Showman lets owned cards reappear
        // in shop/creation pools; Astronomer makes shop Planets free; To the Moon adds an uncapped extra
        // $1/$5 interest tier (the formula coupling stays in EconomyConfig — this var only flags it).
        ALLOW_SHOP_DUPLICATES, PLANETS_FREE, UNCAPPED_INTEREST,
        // Chicot: while owned, the Boss Blind's ability is disabled — a dynamic policy folded from the owned
        // jokers (sell Chicot and the boss re-arms), read synchronously at blind setup like the others above.
        BOSS_ABILITY_DISABLED }

    /** {@code base + scale * (live run-state quantity)} (per dollar, per remaining hand, ...). */
    record RunVar(Property which, double base, double scale) implements Value {}

    /** {@code base + scale * floor(runVar / per)} — stepwise scaling (Bootstraps: +2 Mult per $5). */
    record RunVarStep(Property which, double base, double scale, double per) implements Value {}

    /** Which end of the held-card range a {@link HeldExtreme} reads. */
    enum Extreme { LOWEST, HIGHEST }

    /** {@code base + scale * (lowest|highest base-chip value among held cards)} — Raised Fist. Stone cards
     *  are ignored; an empty hand resolves to {@code base}. */
    record HeldExtreme(Extreme end, double base, double scale) implements Value {}

    /** {@code base + scale * (cards of rank id {@code rankId} in the full deck)} — Cloud 9 ($1 per 9). */
    record DeckRankCount(int rankId, double base, double scale) implements Value {}

    /** A uniform random integer magnitude in {@code [min, max]} (Misprint 0..23), popped from a game-long
     *  queue keyed by {@code seedKey} so both players roll the same sequence. */
    record Random(double min, double max, String seedKey) implements Value {}

    /** Which deck/run aggregate a {@link Stat} reads. */
    enum Which { DECK_SIZE, DECK_REMAINING, ENHANCED_CARD_COUNT, DECK_ENH_COUNT, OWNED_JOKERS,
        EMPTY_JOKER_SLOTS, CARDS_BELOW_FULL }

    /** A standard full deck size — Erosion's reference point. */
    int FULL_DECK = 52;

    /** {@code base + scale * (deck/run aggregate)} — Blue (deck remaining), Abstract (jokers owned), etc.
     *  {@code enhancement} is used only by DECK_ENH_COUNT. */
    record Stat(Which which, double base, double scale, Enhancement enhancement) implements Value {}
}
