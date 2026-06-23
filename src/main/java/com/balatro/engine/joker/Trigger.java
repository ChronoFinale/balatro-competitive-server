package com.balatro.engine.joker;

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
    MODIFY_SCORING_HAND, // build the scoring set (Splash add-all, joker add/remove) — set phase
    BEFORE,             // before any card scores
    INITIAL_SCORING_STEP, // right after base chips/mult, before per-card scoring (effect phase)
    ON_SCORED,          // per played scoring card (individual, play area)
    ON_HELD,            // per held-in-hand card (individual, hand area)
    REPETITION_PLAYED,  // retrigger count for a played card
    REPETITION_HELD,    // retrigger count for a held card
    JOKER_MAIN,         // the joker's main effect
    ON_OTHER_JOKER,     // a joker reacting to another joker
    FINAL_SCORING_STEP, // after the main joker pass, before final chips×mult (effect phase)
    AFTER,              // after scoring resolves (pre-destruction)
    DEBUFFED_HAND,      // a boss vetoed the hand (raised by the Blind, WP-M0-5)
    DESTROYING_CARD,    // a card is about to be destroyed (destruction pass, WP-M0-7)
    REMOVE_PLAYING_CARDS, // cards were removed this evaluation (carries removedCards)

    // ---- lifecycle (raised by GameEvents) ----
    BLIND_SELECTED,     // a blind is chosen (the boss's blind-start effects: Amber Acorn flip+shuffle)
    PRE_HAND,           // before a played hand scores (the boss's pre-hand effects: Crimson Heart joker-disable)
    ON_HAND_PLAYED,     // a hand was played + scored (the boss's per-hand effects: Tooth/Ox/Arm/Hook)
    FIRST_HAND_DRAWN,   // first hand of the round dealt
    PRE_DISCARD,        // before a discard resolves (sees the discarded set)
    ON_DISCARD,         // per discarded card
    END_OF_ROUND,       // round won — economy/interest/gold
    USE_CONSUMABLE,     // a Tarot/Planet/Spectral is used
    BUY_CARD,           // a card bought in the shop
    SELL_CARD,          // another card sold
    SELL_SELF,          // this joker sold
    REROLL_SHOP,        // shop rerolled
    SHOP_ENTER,         // arriving at the shop (Penny Pincher: gain $ per Nemesis spend)
    SHOP_EXIT,          // leaving the shop
    SKIP_BLIND,         // a blind skipped (tag taken)
    OPEN_BOOSTER,       // a booster pack opened
    SKIP_BOOSTER,       // a booster pack skipped (Red Card)
    CARD_ADDED,         // a playing card added to the deck
    CARD_DESTROYED,     // a card destroyed

    // PvP/Match moments — raised by the Match (which holds both runs), so a joker reacts to the two-player
    // flow as data: Speedrun on arrival, Pizza on resolution. The cross-player half is supplied by the Match.
    PVP_BLIND_REACHED,  // this run entered a PvP (Nemesis) blind
    PVP_BLIND_ENDED     // a PvP blind resolved (consume / grant time)
}
