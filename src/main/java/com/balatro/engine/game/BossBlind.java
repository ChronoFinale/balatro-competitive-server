package com.balatro.engine.game;

import com.balatro.engine.joker.def.Condition;
import com.balatro.engine.joker.def.Modify;
import java.util.List;

/**
 * A boss blind as DATA — built via the fluent {@link Bosses} builder. A boss is mostly a set of
 * <b>rule modifiers</b> (the modifier family, distinct from a joker's effects): which cards are
 * debuffed, whether base score is halved, what a legal play is, plus a few per-hand event effects.
 *
 * <p>Crucially, {@code debuff} reuses the same {@link Condition} vocabulary as jokers — "Clubs don't
 * score" is {@code card().suit(CLUBS)}, "faces don't score" is {@code card().isFace()}. One language
 * of conditions; a joker attaches it to an effect, a boss attaches it to a modifier.
 */
public record BossBlind(
        String key,
        String name,
        String effect,
        int minAnte,
        boolean finisher,
        double reqMult,                // score requirement = blind amount × this
        int reward,
        // --- resource modifiers, as data: the boss's hand/discard/hand-size changes are Modifys on the
        //     same game variables a joker/deck/voucher touch (Needle = set(HANDS_LEFT,1), Manacle =
        //     add(HAND_SIZE,-1), Water = set(DISCARDS_LEFT,0)). Folded by Run alongside everyone else. ---
        List<Modify> mods,
        Condition debuff,              // cards matching this don't score (Club/Goad/Window/Head/Plant); null = none
        boolean halveBase,             // The Flint: base chips & mult halved
        // --- per-hand event effects (Run.applyBossOnHandPlayed) ---
        int dollarsPerCardPlayed,      // The Tooth: -$1 per card played
        boolean zeroMoneyOnMostPlayed, // The Ox
        boolean delevelPlayedHand,     // The Arm
        // --- hand-legality: a play is only legal if it satisfies this condition (Run.play, pre-score).
        //     Reuses the shared Cond vocabulary — The Psychic is requires(playedHand().sizeAtLeast(5)). ---
        Condition requires,
        // --- draw-time hidden information: cards drawn under this rule arrive face down (Run marks them,
        //     ClientView hides them). House/Wheel/Mark/Fish. null = none. ---
        FaceDownRule faceDown,
        // --- The Serpent: after each play/discard, draw exactly this many cards instead of refilling
        //     to hand size (so the hand shrinks). -1 = normal refill. ---
        int drawOnRefill,
        // --- The Hook: after each played hand, discard this many random held cards (then refill). 0 = none. ---
        int discardAfterPlay,
        // --- Verdant Leaf: selling any joker disables this boss for the rest of the blind (like Luchador). ---
        boolean disableOnJokerSell,
        // --- Crimson Heart: before each played hand, one random Joker is disabled for that hand. ---
        boolean disableRandomJokerPerHand,
        // --- Amber Acorn: at blind start, the Jokers are flipped face down (hidden in the view) and
        //     their order is shuffled (which reorders scoring). ---
        boolean flipAndShuffleJokers,
        // --- Cerulean Bell: one held card is force-selected — every played hand must include it. ---
        boolean forcesCardSelection) {

    /** The baseline boss score requirement (×blind amount); every boss is at least this big. */
    public static final double BASELINE_REQ_MULT = 2.0;

    /**
     * Does this boss carry any ability beyond being a baseline (×2) blind? A boss whose only
     * "effect" is the default requirement and no debuff/resource/per-hand/legality/face-down/joker
     * rule is a pure description no-op — the coverage net rejects that (see BossCoverageTest).
     */
    public boolean hasAbility() {
        return reqMult != BASELINE_REQ_MULT || !mods.isEmpty() || debuff != null || halveBase
                || dollarsPerCardPlayed != 0 || zeroMoneyOnMostPlayed || delevelPlayedHand
                || requires != null || faceDown != null || drawOnRefill != -1 || discardAfterPlay != 0
                || disableOnJokerSell || disableRandomJokerPerHand || flipAndShuffleJokers
                || forcesCardSelection;
    }

    /** When the {@link FaceDownRule} fires, relative to which deal put the card in hand. */
    public enum When {
        INITIAL_DEAL, // The House: only the hand dealt at blind start
        AFTER_PLAY,   // The Fish: only cards drawn to replace a played hand
        ALWAYS        // The Mark / The Wheel: every card drawn, all blind long
    }

    /**
     * A draw-time face-down rule. A card drawn during a matching {@link When} arrives face down if it
     * satisfies {@code card} (null = any card) and clears the {@code chance} roll (0 = always).
     * The Mark is {@code (ALWAYS, card().isFace(), 0)}; the Wheel is {@code (ALWAYS, null, 1/7)};
     * the House is {@code (INITIAL_DEAL, null, 0)}; the Fish is {@code (AFTER_PLAY, null, 0)}.
     */
    public record FaceDownRule(When when, Condition card, double chance) {}
}
