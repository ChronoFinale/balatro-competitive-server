package com.balatromp.engine.joker;

/**
 * The complete set of moments a joker can react to — Balatro's {@code context.*}
 * flags (spec §2), now unified. This is the whole event surface: there is no
 * separate "trigger system", just this enum dispatched through
 * {@link Joker#calculate(EvaluationContext)}.
 *
 * <p>SCORING triggers are raised in order by {@code ScoringEngine.evaluate_play}.
 * LIFECYCLE triggers are raised by {@code GameEvents} from the round/shop/discard
 * flows. Adding a new trigger = add a value here + one raise-point + jokers that
 * branch on it. No redesign.
 */
public enum Trigger {

    // ---- scoring pipeline (ordered, raised by ScoringEngine) ----
    BEFORE,             // before any card scores
    ON_SCORED,          // per played scoring card (individual, play area)
    ON_HELD,            // per held-in-hand card (individual, hand area)
    REPETITION_PLAYED,  // retrigger count for a played card
    REPETITION_HELD,    // retrigger count for a held card
    JOKER_MAIN,         // the joker's main effect
    ON_OTHER_JOKER,     // a joker reacting to another joker
    AFTER,              // after scoring resolves (pre-destruction)

    // ---- lifecycle (raised by GameEvents) ----
    BLIND_SELECTED,     // a blind is chosen
    FIRST_HAND_DRAWN,   // first hand of the round dealt
    PRE_DISCARD,        // before a discard resolves (sees the discarded set)
    ON_DISCARD,         // per discarded card
    END_OF_ROUND,       // round won — economy/interest/gold
    USE_CONSUMABLE,     // a Tarot/Planet/Spectral is used
    BUY_CARD,           // a card bought in the shop
    SELL_CARD,          // another card sold
    SELL_SELF,          // this joker sold
    REROLL_SHOP,        // shop rerolled
    SHOP_EXIT,          // leaving the shop
    SKIP_BLIND,         // a blind skipped (tag taken)
    OPEN_BOOSTER,       // a booster pack opened
    CARD_ADDED,         // a playing card added to the deck
    CARD_DESTROYED      // a card destroyed
}
