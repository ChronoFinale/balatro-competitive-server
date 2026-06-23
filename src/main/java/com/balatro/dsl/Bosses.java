package com.balatro.dsl;

import com.balatro.engine.game.*;

import com.balatro.engine.joker.def.Condition;
import com.balatro.engine.joker.def.Modify;
import com.balatro.engine.joker.def.Value;

/**
 * Fluent builder for {@link BossBlind}s — a boss reads as the handful of effects it actually has,
 * instead of a wall of positional constructor args. Same data, far more legible.
 *
 * <pre>{@code
 * Bosses.of("bl_tooth", "The Tooth").desc("Lose $1 per card played").dollarsPerCard(-1).build()
 * Bosses.of("bl_club", "The Club").desc("Clubs don't score").debuffsSuit(CLUBS).build()
 * }</pre>
 *
 * Sensible defaults (the common boss): ante 1, requirement x2, reward $5, no overrides.
 */
public final class Bosses {

    private final String key;
    private final String name;
    private String effect = "";
    private int minAnte = 1;
    private boolean finisher = false;
    private double reqMult = 2.0;
    private int reward = 5;
    private final java.util.List<Modify> mods = new java.util.ArrayList<>();
    private Condition debuff = null;
    private boolean halveBase = false;
    private int dollarsPerCardPlayed = 0;
    private boolean zeroMoneyOnMostPlayed = false;
    private boolean delevelPlayedHand = false;
    private Condition requires = null;
    private BossBlind.FaceDownRule faceDown = null;
    private int drawOnRefill = -1;
    private int discardAfterPlay = 0;
    private boolean disableOnJokerSell = false;
    private boolean disableRandomJokerPerHand = false;
    private boolean flipAndShuffleJokers = false;
    private boolean forcesCardSelection = false;

    private Bosses(String key, String name) {
        this.key = key;
        this.name = name;
    }

    public static Bosses of(String key, String name) { return new Bosses(key, name); }

    public Bosses desc(String d) { this.effect = d; return this; }

    public Bosses minAnte(int a) { this.minAnte = a; return this; }

    /** A finisher / showdown boss (ante-8). */
    public Bosses finisher() { this.finisher = true; this.minAnte = 8; this.reward = 8; return this; }

    /** Score requirement = blind amount x {@code mult} (default 2). */
    public Bosses requirement(double mult) { this.reqMult = mult; return this; }

    public Bosses reward(int r) { this.reward = r; return this; }

    /** The Needle: this round has exactly {@code n} hands ({@code set(HANDS_LEFT, n)}). */
    public Bosses hands(int n) { mods.add(Modify.set(Value.Var.HANDS_LEFT, n)); return this; }

    /** The Water: this round has exactly {@code n} discards ({@code set(DISCARDS_LEFT, n)}). */
    public Bosses discards(int n) { mods.add(Modify.set(Value.Var.DISCARDS_LEFT, n)); return this; }

    /** The Manacle: {@code delta} hand size ({@code add(HAND_SIZE, delta)}). */
    public Bosses handSize(int delta) { mods.add(Modify.add(Value.Var.HAND_SIZE, delta)); return this; }

    /** Cards matching {@code cond} don't score — reuses the joker condition vocabulary
     *  ({@code Cond.card().suit(CLUBS)}, {@code Cond.card().isFace()}). */
    public Bosses debuffs(Condition cond) { this.debuff = cond; return this; }

    public Bosses halvesBase() { this.halveBase = true; return this; }

    /** The Tooth: lose ${@code -d} per card played (pass a negative). */
    public Bosses dollarsPerCard(int d) { this.dollarsPerCardPlayed = d; return this; }

    /** The Ox: playing your most-played hand sets money to $0. */
    public Bosses zeroMoneyOnMostPlayed() { this.zeroMoneyOnMostPlayed = true; return this; }

    /** The Arm: the played poker hand drops a level. */
    public Bosses delevelsPlayedHand() { this.delevelPlayedHand = true; return this; }

    /** A play is only legal if it satisfies {@code cond} — reuses the joker condition vocabulary
     *  (The Psychic: {@code requires(playedHand().sizeAtLeast(5))}). */
    public Bosses requires(Condition cond) { this.requires = cond; return this; }

    /** The Mark: cards matching {@code cond} are dealt face down ({@code drawsFaceDown(card().isFace())}). */
    public Bosses drawsFaceDown(Condition cond) {
        this.faceDown = new BossBlind.FaceDownRule(BossBlind.When.ALWAYS, cond, 0);
        return this;
    }

    /** The Wheel: each drawn card has a {@code 1-in-n} chance of arriving face down. */
    public Bosses drawsFaceDownOneIn(int n) {
        this.faceDown = new BossBlind.FaceDownRule(BossBlind.When.ALWAYS, null, 1.0 / n);
        return this;
    }

    /** The House: the hand dealt at the start of the blind is face down. */
    public Bosses drawsInitialHandFaceDown() {
        this.faceDown = new BossBlind.FaceDownRule(BossBlind.When.INITIAL_DEAL, null, 0);
        return this;
    }

    /** The Fish: cards drawn to replace a played hand arrive face down. */
    public Bosses drawsFaceDownAfterPlay() {
        this.faceDown = new BossBlind.FaceDownRule(BossBlind.When.AFTER_PLAY, null, 0);
        return this;
    }

    /** The Serpent: after each play or discard, always draw exactly {@code n} cards (no refill to size). */
    public Bosses drawsExactly(int n) { this.drawOnRefill = n; return this; }

    /** The Hook: after each played hand, discard {@code n} random held cards (then refill). */
    public Bosses discardsRandomAfterPlay(int n) { this.discardAfterPlay = n; return this; }

    /** Verdant Leaf: selling any Joker disables this boss for the rest of the blind. Pairs with
     *  {@code debuffs(always())} — every card is debuffed until you sell a Joker to lift it. */
    public Bosses disabledBySellingJoker() { this.disableOnJokerSell = true; return this; }

    /** Crimson Heart: before each played hand, one random Joker is switched off for that hand. */
    public Bosses disablesRandomJokerEachHand() { this.disableRandomJokerPerHand = true; return this; }

    /** Amber Acorn: at blind start, flip the Jokers face down and shuffle their order. */
    public Bosses flipsAndShufflesJokers() { this.flipAndShuffleJokers = true; return this; }

    /** Cerulean Bell: one held card is force-selected — every played hand must include it. */
    public Bosses forcesOneCardSelected() { this.forcesCardSelection = true; return this; }

    /** Integer-valued doubles render without a trailing ".0" (4.0 -> "4") for clean description text. */
    private static Object fmt(double d) {
        return d == Math.rint(d) ? (Object) (long) d : (Object) d;
    }

    public BossBlind build() {
        // Description is localization data: if not set explicitly, pull the template from Loc keyed by the
        // boss key and fill ${field} placeholders from this boss's own values (so a number lives once).
        String text = effect.isEmpty()
                ? com.balatro.engine.i18n.Loc.fill(key, java.util.Map.of(
                        "reqMult", fmt(reqMult), "minAnte", minAnte, "reward", reward,
                        "dollarsPerCardPlayed", dollarsPerCardPlayed, "drawOnRefill", drawOnRefill,
                        "discardAfterPlay", discardAfterPlay))
                : effect;
        return new BossBlind(key, name, text, minAnte, finisher, reqMult, reward,
                java.util.List.copyOf(mods), debuff, halveBase,
                dollarsPerCardPlayed, zeroMoneyOnMostPlayed, delevelPlayedHand, requires, faceDown,
                drawOnRefill, discardAfterPlay, disableOnJokerSell, disableRandomJokerPerHand,
                flipAndShuffleJokers, forcesCardSelection);
    }
}
