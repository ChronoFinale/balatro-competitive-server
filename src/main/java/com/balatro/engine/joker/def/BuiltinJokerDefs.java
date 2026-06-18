package com.balatro.engine.joker.def;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.Trigger;
import com.balatro.engine.joker.def.Condition.Cmp;
import com.balatro.engine.joker.def.EffectTemplate.Op;
import static com.balatro.engine.joker.def.Cond.always;
import static com.balatro.engine.joker.def.Cond.all;
import static com.balatro.engine.joker.def.Cond.any;
import static com.balatro.engine.joker.def.Cond.not;
import static com.balatro.engine.joker.def.Cond.card;
import static com.balatro.engine.joker.def.Cond.playedHand;
import static com.balatro.engine.joker.def.Cond.held;
import static com.balatro.engine.joker.def.Cond.discard;
import static com.balatro.engine.joker.def.Cond.using;
import static com.balatro.engine.joker.def.Cond.state;
import static com.balatro.engine.joker.def.Target.CHIPS;
import static com.balatro.engine.joker.def.Target.DOLLARS;
import static com.balatro.engine.joker.def.Target.MULT;
import java.util.List;

/**
 * A batch of real Balatro jokers expressed as pure {@link JokerDef} data — they
 * register as built-ins (so they appear in the shop and score authoritatively)
 * exactly like the hand-coded set. Every value here (cost, rarity, sprite
 * position, effect magnitude) is transcribed from the decompiled
 * {@code game.lua} centers table, so they match the real game's numbers.
 *
 * <p>This is the "content as data" path in action: each joker below is a few
 * lines of declarative rules, no code. The same building blocks back the joker
 * builder UI.
 */
public final class BuiltinJokerDefs {

    private BuiltinJokerDefs() {}

    /**
     * Behavior variants of existing jokers, keyed by variant name. A match's ruleset
     * picks the variant (e.g. single-player "default" vs "multiplayer"); the joker key
     * stays the same, only the rules differ. Example: multiplayer Hanging Chad retriggers
     * the first card once (2x) instead of twice (3x).
     */
    public static java.util.Map<String, List<JokerDef>> variants() {
        return java.util.Map.of("multiplayer", List.of(
                // MP Hanging Chad: retrigger the first AND second scored card 1 additional time each.
                new JokerDef("j_hanging_chad", "Hanging Chad",
                        "Retrigger the first and second played cards 1 additional time each (multiplayer)",
                        "Common", 4, 1, 5, null, null, true, List.of(),
                        List.of(new Rule(Trigger.REPETITION_PLAYED, new Condition.ScoredAmongFirst(2),
                                new EffectTemplate(Op.REPETITIONS, new Value.Const(1))))),
                // MP Seltzer: retrigger window is 8 hands (vanilla 10).
                new JokerDef("j_seltzer", "Seltzer",
                        "Retrigger all played cards for the first 8 hands after it is acquired (multiplayer)",
                        "Uncommon", 6, 8, 16, null, null, true, List.of(),
                        List.of(new Rule(Trigger.REPETITION_PLAYED, new Condition.HandsSinceAcquire(8),
                                new EffectTemplate(Op.REPETITIONS, new Value.Const(1))))),
                // MP Golden Ticket: $3 per scored Gold card (vanilla $4), Uncommon.
                new JokerDef("j_golden_ticket", "Golden Ticket",
                        "Played Gold cards give $3 when scored (multiplayer)",
                        "Uncommon", 5, 6, 7, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredEnhancement(Enhancement.GOLD),
                                new EffectTemplate(Op.DOLLARS, new Value.Const(3)))))));
    }

    /** Suit → +3 Mult per played card of that suit (Greedy/Lusty/Wrathful/Gluttonous family). */
    private static JokerDef suitMult(String key, String name, Suit suit, int atlasX, int atlasY) {
        // Templated: the suit and the mult are declared PROPERTIES, and the effect is a function over them.
        // Greedy/Lusty/Wrathful/Gluttonous are this one definition with different props.
        return Jokers.common(key, name).cost(5).atlas(atlasX, atlasY)
                .desc("Each played " + suitName(suit) + " gives +3 Mult")
                .prop("suit", suit).prop("mult", 3)
                .forEachScored(card().suit(suit)).add(MULT, Val.prop("mult"))
                .build();
    }

    private static String suitName(Suit s) {
        return switch (s) {
            case HEARTS -> "Heart";
            case SPADES -> "Spade";
            case CLUBS -> "Club";
            case DIAMONDS -> "Diamond";
        };
    }

    /** "+N Mult if the played hand contains [type]" (Jolly/Zany/Mad/Crazy/Droll family). */
    private static JokerDef typeMult(String key, String name, HandType part, int mult, int ax, int ay) {
        return new JokerDef(key, name, "+" + mult + " Mult if the played hand contains a " + part.display,
                "Common", 4, ax, ay, null, null, true, List.of(),
                List.of(new Rule(Trigger.JOKER_MAIN, new Condition.HandContains(part),
                        new EffectTemplate(Op.MULT, new Value.Const(mult)))));
    }

    /** "+N Chips if the played hand contains [type]" (Sly/Wily/Clever/Devious/Crafty family). */
    private static JokerDef typeChips(String key, String name, HandType part, int chips, int ax, int ay) {
        return new JokerDef(key, name, "+" + chips + " Chips if the played hand contains a " + part.display,
                "Common", 4, ax, ay, null, null, true, List.of(),
                List.of(new Rule(Trigger.JOKER_MAIN, new Condition.HandContains(part),
                        new EffectTemplate(Op.CHIPS, new Value.Const(chips)))));
    }

    /** "xN Mult if the played hand contains [type]" (The Duo/Trio/Family/Order/Tribe). Rare, $8. */
    private static JokerDef typeXMult(String key, String name, HandType part, double xmult, int ax, int ay) {
        return new JokerDef(key, name, "x" + xmult + " Mult if the played hand contains a " + part.display,
                "Rare", 8, ax, ay, null, null, true, List.of(),
                List.of(new Rule(Trigger.JOKER_MAIN, new Condition.HandContains(part),
                        new EffectTemplate(Op.XMULT, new Value.Const(xmult)))));
    }

    /** "Each played [suit] gives +N Chips/Mult" gem joker (Arrowhead/Onyx Agate). Uncommon, $7. */
    private static JokerDef gem(String key, String name, Suit suit, Op op, int amount, int ax, int ay) {
        String unit = op == Op.CHIPS ? " Chips" : " Mult";
        return new JokerDef(key, name, "Each played " + suitName(suit) + " gives +" + amount + unit,
                "Uncommon", 7, ax, ay, null, null, true, List.of(),
                List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredSuit(suit),
                        new EffectTemplate(op, new Value.Const(amount)))));
    }

    public static List<JokerDef> all() {
        return List.of(
                // --- suit-mult family (all four, fully data-driven) ---
                suitMult("j_greedy_joker", "Greedy Joker", Suit.DIAMONDS, 6, 1),
                suitMult("j_lusty_joker", "Lusty Joker", Suit.HEARTS, 7, 1),
                suitMult("j_wrathful_joker", "Wrathful Joker", Suit.SPADES, 8, 1),
                suitMult("j_gluttenous_joker", "Gluttonous Joker", Suit.CLUBS, 9, 1),

                // --- migrated from hand-coded classes: authored fluently — each rule reads as a sentence,
                //     with the quantifier (forEach / whenHand / atEndOfRound) explicit ---
                Jokers.common("j_joker", "Joker").cost(2).atlas(0, 0).desc("+4 Mult")
                        .whenHand().add(MULT, 4).build(),
                Jokers.common("j_sly_joker", "Sly Joker").cost(3).atlas(0, 14)
                        .desc("+50 Chips if the played hand contains a Pair")
                        .whenHand(playedHand().containsPair()).add(CHIPS, 50).build(),
                Jokers.common("j_half", "Half Joker").cost(5).atlas(7, 0)
                        .desc("+20 Mult if 3 or fewer cards are played")
                        .whenHand(playedHand().sizeAtMost(3)).add(MULT, 20).build(),
                Jokers.common("j_even_steven", "Even Steven").cost(4).atlas(8, 3)
                        .desc("Each played even-rank card gives +4 Mult")
                        .forEachScored(card().even()).add(MULT, 4).build(),
                Jokers.uncommon("j_hack", "Hack").cost(6).atlas(5, 2)
                        .desc("Retrigger each played 2, 3, 4, and 5")
                        .retriggerEachScored(card().rankBetween(2, 5)).build(),
                Jokers.common("j_golden", "Golden Joker").cost(6).atlas(9, 2).desc("+$4 at end of round")
                        .atEndOfRound().add(DOLLARS, 4).build(),
                Jokers.common("j_faceless", "Faceless Joker").cost(4).atlas(1, 11)
                        .desc("+$5 if 3 or more face cards are discarded at once")
                        .whenDiscarding(discard().faces(3)).add(DOLLARS, 5).build(),
                Jokers.uncommon("j_constellation", "Constellation").cost(6).atlas(9, 10)
                        .desc("Gains x0.1 Mult per Planet card used")
                        .whenUsing("Planet").gain("planets", 1)
                        .whenHand(state("planets").atLeast(1))
                        .multiply(MULT, Val.xPerState("planets", 0.1)).build(),
                // Ride the Bus — a STATEFUL RESET: streak breaks to 0 on a scoring face, else +1, then +streak Mult.
                // The last hand-coded joker, now data too (mutations only fire at blueprintDepth 0, as before).
                Jokers.common("j_ride_the_bus", "Ride the Bus").cost(6).atlas(1, 6)
                        .desc("+1 Mult per consecutive hand with no face card")
                        .beforeScoring(playedHand().hasFace()).reset("streak")
                        .beforeScoring(playedHand().hasNoFace()).gain("streak", 1)
                        .whenHand().add(MULT, Val.state("streak")).build(),

                // --- meta copiers: the higher-order Copy primitive, now data too. They are themselves
                //     COPYABLE (blueprint_compat=true in real Balatro, game.lua:498/514) — that is what lets
                //     Blueprint copy a Blueprint and chain; the depth guard stops cycles. ---
                Jokers.rare("j_blueprint", "Blueprint").cost(10).atlas(0, 3)
                        .desc("Copies the ability of the Joker to the right")
                        .copies(CopySpec.Selector.RIGHT_NEIGHBOR).build(),
                Jokers.rare("j_brainstorm", "Brainstorm").cost(10).atlas(1, 14)
                        .desc("Copies the ability of the leftmost Joker")
                        .copies(CopySpec.Selector.LEFTMOST).build(),

                // --- type / conditional ---
                new JokerDef("j_jolly", "Jolly Joker", "+8 Mult if the played hand contains a Pair",
                        "Common", 3, 2, 0, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.HandContainsPair(),
                                new EffectTemplate(Op.MULT, new Value.Const(8))))),

                new JokerDef("j_mystic_summit", "Mystic Summit", "+15 Mult when 0 discards remain",
                        "Common", 5, 2, 2, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.DiscardsLeft(Cmp.EQ, 0),
                                new EffectTemplate(Op.MULT, new Value.Const(15))))),

                // --- run-state scaling ---
                new JokerDef("j_banner", "Banner", "+30 Chips for each remaining discard",
                        "Common", 5, 1, 2, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.CHIPS, new Value.RunVar(Value.Var.DISCARDS_LEFT, 0, 30))))),

                new JokerDef("j_bull", "Bull", "+2 Chips for each $1 you have",
                        "Uncommon", 6, 7, 14, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.CHIPS, new Value.RunVar(Value.Var.MONEY, 0, 2))))),

                // --- per-card chips ---
                new JokerDef("j_scary_face", "Scary Face", "Played face cards give +30 Chips",
                        "Common", 4, 2, 3, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredIsFace(),
                                new EffectTemplate(Op.CHIPS, new Value.Const(30))))),

                new JokerDef("j_odd_todd", "Odd Todd", "Played odd-rank cards (A,9,7,5,3) give +31 Chips",
                        "Common", 4, 9, 3, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredParity(false),
                                new EffectTemplate(Op.CHIPS, new Value.Const(31))))),

                // --- stateful: gains chips as you play exactly-4-card hands ---
                new JokerDef("j_square", "Square Joker", "Gains +4 Chips if exactly 4 cards are played",
                        "Common", 4, 9, 11, null, null, true,
                        List.of(new Mutation(Trigger.BEFORE, new Condition.PlayedCount(Cmp.EQ, 4),
                                "chips", Mutation.Op.ADD, 4)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("chips", 1),
                                new EffectTemplate(Op.CHIPS, new Value.State("chips", 0, 1))))),

                // --- type-mult family (contains a hand category -> +Mult) ---
                typeMult("j_zany", "Zany Joker", HandType.THREE_OF_A_KIND, 12, 3, 0),
                typeMult("j_mad", "Mad Joker", HandType.TWO_PAIR, 10, 4, 0),
                typeMult("j_crazy", "Crazy Joker", HandType.STRAIGHT, 12, 5, 0),
                typeMult("j_droll", "Droll Joker", HandType.FLUSH, 10, 6, 0),

                // --- type-chips family (contains a hand category -> +Chips) ---
                typeChips("j_wily", "Wily Joker", HandType.THREE_OF_A_KIND, 100, 1, 14),
                typeChips("j_clever", "Clever Joker", HandType.TWO_PAIR, 80, 2, 14),
                typeChips("j_devious", "Devious Joker", HandType.STRAIGHT, 100, 3, 14),
                typeChips("j_crafty", "Crafty Joker", HandType.FLUSH, 80, 4, 14),

                // --- compound effect: Aces give chips AND mult (extra-chain) ---
                new JokerDef("j_scholar", "Scholar", "Played Aces give +20 Chips and +4 Mult",
                        "Common", 4, 0, 4, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredRankBetween(14, 14),
                                new EffectTemplate(Op.CHIPS, new Value.Const(20),
                                        new EffectTemplate(Op.MULT, new Value.Const(4)))))),

                // --- per-card mult ---
                new JokerDef("j_smiley", "Smiley Face", "Played face cards give +5 Mult",
                        "Common", 4, 6, 15, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredIsFace(),
                                new EffectTemplate(Op.MULT, new Value.Const(5))))),

                // --- compound per-card: each played 10 or 4 gives +10 Chips and +4 Mult ---
                new JokerDef("j_walkie_talkie", "Walkie Talkie", "Each played 10 or 4 gives +10 Chips and +4 Mult",
                        "Common", 4, 8, 15, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED,
                                new Condition.Or(List.of(new Condition.ScoredRankBetween(10, 10),
                                        new Condition.ScoredRankBetween(4, 4))),
                                new EffectTemplate(Op.CHIPS, new Value.Const(10),
                                        new EffectTemplate(Op.MULT, new Value.Const(4)))))),

                // --- rank-set via Or: each played A, 2, 3, 5, 8 gives +8 Mult ---
                new JokerDef("j_fibonacci", "Fibonacci", "Each played Ace, 2, 3, 5, or 8 gives +8 Mult",
                        "Uncommon", 8, 1, 5, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.Or(List.of(
                                new Condition.ScoredRankBetween(14, 14), new Condition.ScoredRankBetween(2, 2),
                                new Condition.ScoredRankBetween(3, 3), new Condition.ScoredRankBetween(5, 5),
                                new Condition.ScoredRankBetween(8, 8))),
                                new EffectTemplate(Op.MULT, new Value.Const(8))))),

                // --- held-card additive mult: each Queen held gives +13 Mult ---
                new JokerDef("j_shoot_the_moon", "Shoot the Moon", "Each Queen held in hand gives +13 Mult",
                        "Common", 5, 2, 6, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_HELD, new Condition.ScoredRankBetween(12, 12),
                                new EffectTemplate(Op.MULT, new Value.Const(13))))),

                // --- held-card xmult: each King held gives x1.5 Mult ---
                new JokerDef("j_baron", "Baron", "Each King held in hand gives x1.5 Mult",
                        "Rare", 8, 6, 12, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_HELD, new Condition.ScoredRankBetween(13, 13),
                                new EffectTemplate(Op.XMULT, new Value.Const(1.5))))),

                // --- stateful: gains +15 Chips whenever the played hand contains a Straight ---
                new JokerDef("j_runner", "Runner", "Gains +15 Chips if the played hand contains a Straight",
                        "Common", 5, 3, 10, null, null, true,
                        List.of(new Mutation(Trigger.BEFORE, new Condition.HandContains(HandType.STRAIGHT),
                                "chips", Mutation.Op.ADD, 15)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("chips", 1),
                                new EffectTemplate(Op.CHIPS, new Value.State("chips", 0, 1))))),

                // --- stateful: gains +8 Chips for each played 2 ---
                new JokerDef("j_wee", "Wee Joker", "Gains +8 Chips for each played 2",
                        "Rare", 8, 0, 0, null, null, true,
                        List.of(new Mutation(Trigger.ON_SCORED, new Condition.ScoredRankBetween(2, 2),
                                "chips", Mutation.Op.ADD, 8)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("chips", 1),
                                new EffectTemplate(Op.CHIPS, new Value.State("chips", 0, 1))))),

                // --- xMult "contains" family (The Duo / Trio / Family / Order / Tribe) ---
                typeXMult("j_duo", "The Duo", HandType.PAIR, 2, 5, 4),
                typeXMult("j_trio", "The Trio", HandType.THREE_OF_A_KIND, 3, 6, 4),
                typeXMult("j_family", "The Family", HandType.FOUR_OF_A_KIND, 4, 7, 4),
                typeXMult("j_order", "The Order", HandType.STRAIGHT, 3, 8, 4),
                typeXMult("j_tribe", "The Tribe", HandType.FLUSH, 2, 9, 4),

                // --- gem suit jokers ---
                gem("j_arrowhead", "Arrowhead", Suit.SPADES, Op.CHIPS, 50, 1, 8),
                gem("j_onyx_agate", "Onyx Agate", Suit.CLUBS, Op.MULT, 7, 2, 8),

                // --- retrigger: played face cards trigger again ---
                new JokerDef("j_sock_and_buskin", "Sock and Buskin", "Retrigger all played face cards",
                        "Uncommon", 6, 3, 1, null, null, true, List.of(),
                        List.of(new Rule(Trigger.REPETITION_PLAYED, new Condition.ScoredIsFace(),
                                new EffectTemplate(Op.REPETITIONS, new Value.Const(1))))),

                // --- stateful: gains +2 Mult per shop reroll ---
                new JokerDef("j_flash", "Flash Card", "Gains +2 Mult per shop reroll",
                        "Uncommon", 5, 0, 15, null, null, true,
                        List.of(new Mutation(Trigger.REROLL_SHOP, new Condition.Always(),
                                "mult", Mutation.Op.ADD, 2)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("mult", 1),
                                new EffectTemplate(Op.MULT, new Value.State("mult", 0, 1))))),

                // --- stateful: gains +2 Mult whenever the played hand contains a Two Pair ---
                new JokerDef("j_trousers", "Spare Trousers", "Gains +2 Mult if the played hand contains a Two Pair",
                        "Uncommon", 6, 4, 15, null, null, true,
                        List.of(new Mutation(Trigger.BEFORE, new Condition.HandContains(HandType.TWO_PAIR),
                                "mult", Mutation.Op.ADD, 2)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("mult", 1),
                                new EffectTemplate(Op.MULT, new Value.State("mult", 0, 1))))),

                // --- probabilistic (Chance / Random) ---
                new JokerDef("j_misprint", "Misprint", "Adds +0 to +23 Mult (random each hand)",
                        "Common", 4, 6, 2, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.MULT, new Value.Random(0, 23, "misprint"))))),
                new JokerDef("j_bloodstone", "Bloodstone", "1 in 2 chance each played Heart gives x1.5 Mult",
                        "Uncommon", 7, 0, 8, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED,
                                new Condition.And(List.of(new Condition.ScoredSuit(Suit.HEARTS),
                                        new Condition.Chance(1, 2, "bloodstone"))),
                                new EffectTemplate(Op.XMULT, new Value.Const(1.5))))),

                // --- deck/run-stat scaling (Value.Stat) ---
                new JokerDef("j_blue_joker", "Blue Joker", "+2 Chips for each card remaining in the deck",
                        "Common", 5, 7, 10, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.CHIPS, new Value.Stat(Value.Which.DECK_REMAINING, 0, 2, null))))),
                new JokerDef("j_abstract", "Abstract Joker", "+3 Mult for each Joker",
                        "Common", 4, 3, 3, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.MULT, new Value.Stat(Value.Which.OWNED_JOKERS, 0, 3, null))))),
                new JokerDef("j_stone", "Stone Joker", "+25 Chips for each Stone card in the deck",
                        "Uncommon", 6, 9, 0, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.CHIPS,
                                        new Value.Stat(Value.Which.DECK_ENH_COUNT, 0, 25, Enhancement.STONE))))),
                new JokerDef("j_steel_joker", "Steel Joker", "x0.2 Mult for each Steel card in the deck",
                        "Uncommon", 7, 7, 2, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT,
                                        new Value.Stat(Value.Which.DECK_ENH_COUNT, 1.0, 0.2, Enhancement.STEEL))))),

                // --- MUTATE_CARD: Hiker permanently adds chips to each played card ---
                new JokerDef("j_hiker", "Hiker", "Each played card permanently gains +5 Chips",
                        "Uncommon", 5, 0, 11, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.Always(),
                                EffectTemplate.mutate(CardMod.addChips(5))))),

                // --- MUTATE_CARD: Midas Mask turns played face cards into Gold cards ---
                new JokerDef("j_midas_mask", "Midas Mask", "Played face cards become Gold cards when scored",
                        "Uncommon", 7, 0, 13, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredIsFace(),
                                EffectTemplate.mutate(CardMod.setEnhancement(Enhancement.GOLD))))),

                // --- MUTATE_CARD + stateful xMult: Vampire strips enhancements and grows ---
                new JokerDef("j_vampire", "Vampire", "Gains x0.1 Mult per enhanced card played, removing its enhancement",
                        "Uncommon", 7, 2, 12, null, null, true,
                        List.of(new Mutation(Trigger.ON_SCORED,
                                new Condition.Not(new Condition.ScoredEnhancement(Enhancement.NONE)),
                                "xm", Mutation.Op.ADD, 0.1)),
                        List.of(
                                new Rule(Trigger.ON_SCORED,
                                        new Condition.Not(new Condition.ScoredEnhancement(Enhancement.NONE)),
                                        EffectTemplate.mutate(CardMod.removeEnhancement())),
                                new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("xm", 0.1),
                                        new EffectTemplate(Op.XMULT, new Value.State("xm", 1.0, 1.0))))),

                // --- economy-during-scoring: money earned mid-hand (credited at end) ---
                new JokerDef("j_rough_gem", "Rough Gem", "Each played Diamond gives $1 when scored",
                        "Uncommon", 7, 5, 9, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredSuit(Suit.DIAMONDS),
                                new EffectTemplate(Op.DOLLARS, new Value.Const(1))))),
                new JokerDef("j_business_card", "Business Card", "Played face cards have a 1 in 2 chance to give $2",
                        "Common", 4, 1, 9, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED,
                                new Condition.And(List.of(new Condition.ScoredIsFace(),
                                        new Condition.Chance(1, 2, "business_card"))),
                                new EffectTemplate(Op.DOLLARS, new Value.Const(2))))),

                // --- flat / xMult jokers expressible with the existing algebra ---
                new JokerDef("j_gros_michel", "Gros Michel", "+15 Mult",
                        "Common", 5, 8, 2, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.MULT, new Value.Const(15))))),
                new JokerDef("j_cavendish", "Cavendish", "x3 Mult",
                        "Common", 5, 9, 2, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT, new Value.Const(3))))),
                new JokerDef("j_acrobat", "Acrobat", "x3 Mult on the final hand of the round",
                        "Uncommon", 7, 8, 3, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.HandsLeft(Condition.Cmp.LTE, 1),
                                new EffectTemplate(Op.XMULT, new Value.Const(3))))),
                new JokerDef("j_joker_stencil", "Joker Stencil",
                        "x1 Mult for each empty Joker slot (Joker Stencil included)",
                        "Uncommon", 7, 0, 4, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT,
                                        new Value.Stat(Value.Which.EMPTY_JOKER_SLOTS, 1.0, 1.0, null))))),
                new JokerDef("j_triboulet", "Triboulet", "Played Kings and Queens each give x2 Mult",
                        "Legendary", 20, 4, 15, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredRankBetween(12, 13),
                                new EffectTemplate(Op.XMULT, new Value.Const(2))))),

                // --- ScoredFirst: retrigger the first scored card (Hanging Chad) ---
                new JokerDef("j_hanging_chad", "Hanging Chad", "Retrigger the first played card 2 additional times",
                        "Common", 4, 1, 5, null, null, true, List.of(),
                        List.of(new Rule(Trigger.REPETITION_PLAYED, new Condition.ScoredFirst(),
                                new EffectTemplate(Op.REPETITIONS, new Value.Const(2))))),

                // --- ScoringContainsSuit: suit-coverage xMult jokers ---
                new JokerDef("j_flower_pot", "Flower Pot",
                        "x3 Mult if the scoring hand contains a Diamond, Club, Heart and Spade",
                        "Uncommon", 6, 8, 4, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.And(List.of(
                                new Condition.ScoringContainsSuit(Suit.DIAMONDS),
                                new Condition.ScoringContainsSuit(Suit.CLUBS),
                                new Condition.ScoringContainsSuit(Suit.HEARTS),
                                new Condition.ScoringContainsSuit(Suit.SPADES))),
                                new EffectTemplate(Op.XMULT, new Value.Const(3))))),
                new JokerDef("j_seeing_double", "Seeing Double",
                        "x2 Mult if the scoring hand has a Club and a card of any other suit",
                        "Uncommon", 6, 9, 4, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.And(List.of(
                                new Condition.ScoringContainsSuit(Suit.CLUBS),
                                new Condition.Or(List.of(
                                        new Condition.ScoringContainsSuit(Suit.DIAMONDS),
                                        new Condition.ScoringContainsSuit(Suit.HEARTS),
                                        new Condition.ScoringContainsSuit(Suit.SPADES))))),
                                new EffectTemplate(Op.XMULT, new Value.Const(2))))),

                // --- global hand-evaluation modifiers (HandMod): change what hands form ---
                new JokerDef("j_four_fingers", "Four Fingers",
                        "All Flushes and Straights can be made with 4 cards",
                        "Uncommon", 7, 1, 6, null, null, true, List.of(), List.of(),
                        List.of(com.balatro.engine.hand.HandMod.FOUR_FINGERS)),
                new JokerDef("j_shortcut", "Shortcut",
                        "Allows Straights to be made with gaps of 1 rank",
                        "Uncommon", 7, 2, 6, null, null, true, List.of(), List.of(),
                        List.of(com.balatro.engine.hand.HandMod.SHORTCUT)),
                new JokerDef("j_smeared_joker", "Smeared Joker",
                        "Hearts and Diamonds count as the same suit, Spades and Clubs the same",
                        "Uncommon", 7, 3, 6, null, null, true, List.of(), List.of(),
                        List.of(com.balatro.engine.hand.HandMod.SMEARED)),

                // --- retrigger jokers (existing algebra) ---
                new JokerDef("j_mime", "Mime", "Retrigger all cards held in hand",
                        "Uncommon", 5, 8, 5, null, null, true, List.of(),
                        List.of(new Rule(Trigger.REPETITION_HELD, new Condition.Always(),
                                new EffectTemplate(Op.REPETITIONS, new Value.Const(1))))),
                new JokerDef("j_dusk", "Dusk", "Retrigger all played cards on the final hand of the round",
                        "Uncommon", 5, 9, 5, null, null, true, List.of(),
                        List.of(new Rule(Trigger.REPETITION_PLAYED, new Condition.HandsLeft(Cmp.LTE, 1),
                                new EffectTemplate(Op.REPETITIONS, new Value.Const(1))))),

                // --- Gold-card economy when scored ---
                new JokerDef("j_golden_ticket", "Golden Ticket", "Played Gold cards give $4 when scored",
                        "Common", 5, 6, 7, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredEnhancement(Enhancement.GOLD),
                                new EffectTemplate(Op.DOLLARS, new Value.Const(4))))),

                // --- batch 2: first-face / stat-threshold / held-suit conditions ---
                new JokerDef("j_photograph", "Photograph", "The first scored face card gives x2 Mult",
                        "Common", 5, 4, 7, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredFirstFace(),
                                new EffectTemplate(Op.XMULT, new Value.Const(2))))),
                new JokerDef("j_drivers_license", "Driver's License",
                        "x3 Mult if you have at least 16 enhanced cards in your deck",
                        "Rare", 7, 6, 13, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN,
                                new Condition.ValueAtLeast(
                                        new Value.Stat(Value.Which.ENHANCED_CARD_COUNT, 0, 1, null), 16),
                                new EffectTemplate(Op.XMULT, new Value.Const(3))))),
                new JokerDef("j_blackboard", "Blackboard",
                        "x3 Mult if all cards held in hand are Spades or Clubs",
                        "Uncommon", 6, 0, 7, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN,
                                new Condition.HeldAllSuits(List.of(Suit.SPADES, Suit.CLUBS)),
                                new EffectTemplate(Op.XMULT, new Value.Const(3))))),

                // --- batch 3: global scoring modifiers ---
                new JokerDef("j_pareidolia", "Pareidolia", "All cards are considered face cards",
                        "Uncommon", 5, 5, 6, null, null, true, List.of(), List.of(),
                        List.of(com.balatro.engine.hand.HandMod.PAREIDOLIA)),
                new JokerDef("j_splash", "Splash", "Every played card counts in scoring",
                        "Common", 3, 6, 6, null, null, true, List.of(), List.of(),
                        List.of(com.balatro.engine.hand.HandMod.SPLASH)),

                // --- batch 4: lifecycle counter-scaling (state ships to client, JOKER_MAIN reads it) ---
                new JokerDef("j_green_joker", "Green Joker",
                        "+1 Mult per hand played, -1 Mult per discard",
                        "Common", 4, 6, 5, null, null, true,
                        List.of(new Mutation(Trigger.BEFORE, new Condition.Always(), "m", Mutation.Op.ADD, 1),
                                new Mutation(Trigger.PRE_DISCARD, new Condition.Always(), "m", Mutation.Op.ADD, -1)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("m", 1),
                                new EffectTemplate(Op.MULT, new Value.State("m", 0, 1))))),
                new JokerDef("j_fortune_teller", "Fortune Teller",
                        "+1 Mult per Tarot card used this run",
                        "Common", 6, 8, 8, null, null, true,
                        List.of(new Mutation(Trigger.USE_CONSUMABLE, new Condition.ConsumableType("Tarot"),
                                "tarots", Mutation.Op.ADD, 1)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("tarots", 1),
                                new EffectTemplate(Op.MULT, new Value.State("tarots", 0, 1))))),

                // --- batch 5: stepwise / deck-size scaling values ---
                new JokerDef("j_bootstraps", "Bootstraps", "+2 Mult for every $5 you have",
                        "Uncommon", 6, 9, 8, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.MULT, new Value.RunVarStep(Value.Var.MONEY, 0, 2, 5))))),
                new JokerDef("j_erosion", "Erosion",
                        "+4 Mult for each card below 52 in your full deck",
                        "Uncommon", 6, 7, 8, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.MULT,
                                        new Value.Stat(Value.Which.CARDS_BELOW_FULL, 0, 4, null))))),

                // --- batch 6: passive run modifiers (applied at blind start) ---
                runModJoker("j_juggler", "Juggler", "+1 hand size", "Common", 4, 0, 9,
                        new RunMod(0, 0, 1, false)),
                runModJoker("j_drunkard", "Drunkard", "+1 discard each round", "Common", 4, 1, 10,
                        new RunMod(0, 1, 0, false)),
                runModJoker("j_troubadour", "Troubadour", "+2 hand size, -1 hand each round",
                        "Uncommon", 6, 2, 10, new RunMod(-1, 0, 2, false)),
                runModJoker("j_merry_andy", "Merry Andy", "+1 discard, -1 hand size",
                        "Uncommon", 7, 3, 10, new RunMod(0, 1, -1, false)),
                runModJoker("j_burglar", "Burglar", "+3 hands this round, but no discards",
                        "Uncommon", 6, 4, 10, new RunMod(3, 0, 0, true)),
                new JokerDef("j_stuntman", "Stuntman", "+250 Chips, -2 hand size",
                        "Rare", 7, 5, 10, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.CHIPS, new Value.Const(250)))),
                        List.of(), new RunMod(0, 0, -2, false)),

                // --- batch 7: held-card extreme value ---
                new JokerDef("j_raised_fist", "Raised Fist",
                        "Adds double the rank of the lowest card held in hand to Mult",
                        "Common", 5, 7, 9, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.MULT, new Value.HeldExtreme(true, 0, 2))))),

                // --- batch 8: end-of-round economy (END_OF_ROUND credits DOLLARS) ---
                new JokerDef("j_cloud_9", "Cloud 9", "Earn $1 for each 9 in your full deck at end of round",
                        "Uncommon", 7, 1, 11, null, null, true, List.of(),
                        List.of(new Rule(Trigger.END_OF_ROUND, new Condition.Always(),
                                new EffectTemplate(Op.DOLLARS, new Value.DeckRankCount(9, 0, 1))))),
                new JokerDef("j_rocket", "Rocket",
                        "Earn $1 at end of round; payout grows by $2 each Boss defeated",
                        "Uncommon", 6, 2, 11, null, null, true,
                        List.of(new Mutation(Trigger.END_OF_ROUND, new Condition.BossDefeated(),
                                "bosses", Mutation.Op.ADD, 1)),
                        List.of(new Rule(Trigger.END_OF_ROUND, new Condition.Always(),
                                new EffectTemplate(Op.DOLLARS, new Value.State("bosses", 1, 2))))),
                new JokerDef("j_delayed_gratification", "Delayed Gratification",
                        "Earn $2 per remaining discard at end of round if no discards were used",
                        "Common", 4, 3, 11, null, null, true, List.of(),
                        List.of(new Rule(Trigger.END_OF_ROUND,
                                new Condition.Not(new Condition.ValueAtLeast(
                                        new Value.RunVar(Value.Var.DISCARDS_USED, 0, 1), 1)),
                                new EffectTemplate(Op.DOLLARS,
                                        new Value.RunVar(Value.Var.DISCARDS_LEFT, 0, 2))))),

                // --- batch 9: consumable creation ---
                new JokerDef("j_8_ball", "8 Ball",
                        "1 in 4 chance for each played 8 to create a Tarot card (if room)",
                        "Common", 5, 1, 12, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED,
                                new Condition.And(List.of(new Condition.ScoredRankBetween(8, 8),
                                        new Condition.Chance(1, 4, "8ball"))),
                                EffectTemplate.create(new CreateSpec(CreateSpec.Kind.TAROT))))),
                new JokerDef("j_cartomancer", "Cartomancer", "Create a Tarot card when a blind is selected",
                        "Uncommon", 6, 2, 12, null, null, true, List.of(),
                        List.of(new Rule(Trigger.BLIND_SELECTED, new Condition.Always(),
                                EffectTemplate.create(new CreateSpec(CreateSpec.Kind.TAROT))))),
                new JokerDef("j_vagabond", "Vagabond", "Create a Tarot if a hand is played with $4 or less",
                        "Rare", 8, 3, 12, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN,
                                new Condition.Not(new Condition.MoneyAtLeast(5)),
                                EffectTemplate.create(new CreateSpec(CreateSpec.Kind.TAROT))))),
                new JokerDef("j_superposition", "Superposition",
                        "Create a Tarot if a played hand contains an Ace and a Straight",
                        "Common", 5, 4, 12, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.And(List.of(
                                new Condition.HandContains(HandType.STRAIGHT),
                                new Condition.ValueAtLeast(new Value.Count(Value.Source.SCORING,
                                        new Condition.ScoredRankBetween(14, 14), 0, 1), 1))),
                                EffectTemplate.create(new CreateSpec(CreateSpec.Kind.TAROT))))),
                new JokerDef("j_seance", "Seance", "Create a Spectral if the played hand is a Straight Flush",
                        "Uncommon", 6, 5, 12, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.HandIs(HandType.STRAIGHT_FLUSH),
                                EffectTemplate.create(new CreateSpec(CreateSpec.Kind.SPECTRAL))))),

                // --- batch 11: joker / playing-card creation + card destruction ---
                new JokerDef("j_marble", "Marble Joker", "Adds a Stone card to your deck when a blind is selected",
                        "Uncommon", 6, 6, 12, null, null, true, List.of(),
                        List.of(new Rule(Trigger.BLIND_SELECTED, new Condition.Always(),
                                EffectTemplate.create(new CreateSpec(CreateSpec.Kind.PLAYING_CARD, 1, null,
                                        Enhancement.STONE))))),
                new JokerDef("j_riff_raff", "Riff-Raff", "Create 2 Common jokers when a blind is selected (if room)",
                        "Common", 6, 7, 12, null, null, true, List.of(),
                        List.of(new Rule(Trigger.BLIND_SELECTED, new Condition.Always(),
                                EffectTemplate.create(new CreateSpec(CreateSpec.Kind.JOKER, 2, "Common", null))))),
                new JokerDef("j_sixth_sense", "Sixth Sense",
                        "Play a single 6: destroy it and create a Spectral",
                        "Uncommon", 6, 8, 12, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED,
                                new Condition.And(List.of(new Condition.PlayedCount(Condition.Cmp.EQ, 1),
                                        new Condition.ScoredRankBetween(6, 6))),
                                new EffectTemplate(Op.DESTROY_SCORED, null,
                                        EffectTemplate.create(new CreateSpec(CreateSpec.Kind.SPECTRAL)))))),

                // --- batch 12: hand level-up ---
                new JokerDef("j_space", "Space Joker", "1 in 4 chance to upgrade the level of the played hand",
                        "Uncommon", 5, 0, 13, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Chance(1, 4, "space"),
                                EffectTemplate.levelUpHand(1)))),
                // --- batch 13: destruction-counting xMult (CARD_DESTROYED) ---
                new JokerDef("j_glass_joker", "Glass Joker",
                        "Gains x0.75 Mult for every Glass card that is destroyed",
                        "Uncommon", 6, 5, 13, null, null, true,
                        List.of(new Mutation(Trigger.CARD_DESTROYED,
                                new Condition.ScoredEnhancement(Enhancement.GLASS), "x", Mutation.Op.ADD, 0.75)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT, new Value.State("x", 1.0, 1.0))))),
                new JokerDef("j_canio", "Canio",
                        "Gains x1 Mult when a face card is destroyed",
                        "Legendary", 20, 6, 15, null, null, true,
                        List.of(new Mutation(Trigger.CARD_DESTROYED, new Condition.ScoredIsFace(),
                                "x", Mutation.Op.ADD, 1)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT, new Value.State("x", 1.0, 1.0))))),
                new JokerDef("j_yorick", "Yorick", "Gains x1 Mult for every 23 cards discarded",
                        "Legendary", 20, 7, 15, null, null, true,
                        List.of(new Mutation(Trigger.PRE_DISCARD, new Condition.Always(), "d",
                                Mutation.Op.ADD, 1, new Condition.Always())),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT, new Value.StateStep("d", 1.0, 1.0, 23))))),
                new JokerDef("j_hit_the_road", "Hit the Road",
                        "Gains x0.5 Mult for every Jack discarded this round",
                        "Rare", 8, 7, 13, null, null, true,
                        List.of(new Mutation(Trigger.BLIND_SELECTED, new Condition.Always(),
                                        "x", Mutation.Op.RESET, 0),
                                // +0.5 per Jack in the discarded set (count-mutation)
                                new Mutation(Trigger.PRE_DISCARD, new Condition.Always(), "x",
                                        Mutation.Op.ADD, 0.5, new Condition.ScoredRankBetween(11, 11))),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT, new Value.State("x", 1.0, 1.0))))),

                new JokerDef("j_burnt", "Burnt Joker", "Upgrade the level of the first discarded poker hand each round",
                        "Rare", 8, 1, 13, null, null, true,
                        // count discards this round (reset at blind select); mutations run before rules,
                        // so on the first discard the counter is exactly 1 when the rule is checked.
                        List.of(new Mutation(Trigger.BLIND_SELECTED, new Condition.Always(),
                                        "discards", Mutation.Op.RESET, 0),
                                new Mutation(Trigger.PRE_DISCARD, new Condition.Always(),
                                        "discards", Mutation.Op.ADD, 1)),
                        List.of(new Rule(Trigger.PRE_DISCARD,
                                new Condition.And(List.of(new Condition.StateAtLeast("discards", 1),
                                        new Condition.Not(new Condition.StateAtLeast("discards", 2)))),
                                EffectTemplate.levelUpHand(1)))),

                // --- batch 16: card copy (DNA) + sealed card creation (Certificate) ---
                new JokerDef("j_dna", "DNA",
                        "If the first hand of the round is a single card, add a permanent copy to your deck",
                        "Rare", 8, 9, 12, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.And(List.of(
                                new Condition.PlayedCount(Condition.Cmp.EQ, 1),
                                new Condition.Not(new Condition.ValueAtLeast(
                                        new Value.RunVar(Value.Var.HANDS_PLAYED, 0, 1), 1)))),
                                EffectTemplate.copyScored()))),
                new JokerDef("j_hologram", "Hologram",
                        "Gains x0.25 Mult for every playing card added to your deck",
                        "Uncommon", 7, 8, 13, null, null, true,
                        List.of(new Mutation(Trigger.CARD_ADDED, new Condition.Always(),
                                "x", Mutation.Op.ADD, 0.25)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT, new Value.State("x", 1.0, 1.0))))),
                new JokerDef("j_certificate", "Certificate",
                        "When a blind is selected, add a random playing card with a random seal to your deck",
                        "Uncommon", 6, 0, 13, null, null, true, List.of(),
                        List.of(new Rule(Trigger.BLIND_SELECTED, new Condition.Always(),
                                EffectTemplate.create(new CreateSpec(CreateSpec.Kind.PLAYING_CARD, 1,
                                        null, null, null, true))))),

                // --- batch 18: decay jokers (run-long counters + clamped values) ---
                new JokerDef("j_ice_cream", "Ice Cream", "+100 Chips, -5 Chips per hand played",
                        "Common", 5, 2, 13, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.CHIPS, new Value.Clamp(
                                        new Value.RunVar(Value.Var.HANDS_PLAYED_TOTAL, 100, -5), 0, 1e9))))),
                new JokerDef("j_popcorn", "Popcorn", "+20 Mult, -4 Mult per round played",
                        "Common", 5, 3, 13, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.MULT, new Value.Clamp(
                                        new Value.RunVar(Value.Var.ROUNDS_PLAYED, 20, -4), 0, 1e9))))),
                // --- batch 42: Matador (boss-ability interaction) ---
                new JokerDef("j_matador", "Matador",
                        "Earn $8 if the played hand triggers the Boss Blind's ability",
                        "Uncommon", 7, 1, 18, null, null, true, List.of(), List.of()),

                // --- batch 41: tags (Diet Cola) ---
                new JokerDef("j_diet_cola", "Diet Cola", "Sell this to create a free Double Tag",
                        "Uncommon", 6, 0, 18, null, null, true, List.of(), List.of()),

                // --- batch 40: booster packs (Hallucination, Red Card) ---
                new JokerDef("j_hallucination", "Hallucination",
                        "1 in 2 chance to create a Tarot card when a booster pack is opened",
                        "Common", 4, 1, 17, null, null, true, List.of(),
                        List.of(new Rule(Trigger.OPEN_BOOSTER, new Condition.Chance(1, 2, "hallucination"),
                                EffectTemplate.create(new CreateSpec(CreateSpec.Kind.TAROT))))),
                new JokerDef("j_red_card", "Red Card", "Gains +3 Mult when a booster pack is skipped",
                        "Common", 5, 2, 17, null, null, true,
                        List.of(new Mutation(Trigger.SKIP_BOOSTER, new Condition.Always(),
                                "mult", Mutation.Op.ADD, 3)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("mult", 1),
                                new EffectTemplate(Op.MULT, new Value.State("mult", 0, 1))))),

                // --- batch 39: Showman (allow duplicate shop offerings; disables the skip-if-owned rule) ---
                new JokerDef("j_showman", "Showman",
                        "Joker and Consumable cards may appear multiple times in the shop",
                        "Uncommon", 5, 0, 17, null, null, true, List.of(), List.of()),

                // --- batch 47: Pizza (consumed at PvP end -> temporary discards) ---
                new JokerDef("j_pizza", "Pizza",
                        "At the end of the next PvP blind, consumed for +1 discard to you and +2 to your Nemesis",
                        "Uncommon", 5, 0, 18, null, null, true, List.of(), List.of()),

                // --- batch 46: Speedrun (reach PvP first -> Spectral; match-coordinated) ---
                new JokerDef("j_speedrun", "Speedrun",
                        "If you reach a PvP blind before your Nemesis, create a random Spectral",
                        "Uncommon", 6, 9, 17, null, null, true, List.of(), List.of()),

                // --- batch 45: Penny Pincher (Nemesis shop-spend economy) ---
                new JokerDef("j_penny_pincher", "Penny Pincher",
                        "On entering the shop, gain $1 for every $3 your Nemesis spent last ante",
                        "Uncommon", 6, 8, 17, null, null, true, List.of(), List.of()),

                // --- batch 44: more Nemesis jokers (Skip-Off, Let's Go Gambling) ---
                new JokerDef("j_skip_off", "Skip-Off",
                        "+1 Hand and +1 Discard per additional blind skipped vs your Nemesis",
                        "Uncommon", 6, 6, 17, null, null, true, List.of(), List.of()),
                new JokerDef("j_lets_go_gambling", "Let's Go Gambling",
                        "1 in 4 chance for x4 Mult and $10",
                        "Uncommon", 6, 7, 17, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Chance(1, 4, "gambling"),
                                new EffectTemplate(Op.XMULT, new Value.Const(4),
                                        new EffectTemplate(Op.DOLLARS, new Value.Const(10)))))),

                // --- batch 43: multiplayer-exclusive "Nemesis" jokers (read opponent state) ---
                new JokerDef("j_pacifist", "Pacifist", "x10 Mult while not in a PvP blind",
                        "Uncommon", 6, 2, 17, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Not(new Condition.InPvpBlind()),
                                new EffectTemplate(Op.XMULT, new Value.Const(10))))),
                new JokerDef("j_defensive_joker", "Defensive Joker",
                        "+125 Chips for every life you are behind your Nemesis",
                        "Uncommon", 6, 3, 17, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.CHIPS, new Value.RunVar(Value.Var.OPP_LIVES_BEHIND, 0, 125))))),
                new JokerDef("j_conjoined", "Conjoined Joker",
                        "In a PvP blind, x0.5 Mult per hand your Nemesis has left (max x3)",
                        "Rare", 8, 4, 17, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.InPvpBlind(),
                                new EffectTemplate(Op.XMULT, new Value.Clamp(
                                        new Value.RunVar(Value.Var.OPP_HANDS_LEFT, 1, 0.5), 1, 3))))),
                new JokerDef("j_taxes", "Taxes",
                        "+4 Mult per card your Nemesis has sold since the last PvP blind",
                        "Uncommon", 6, 5, 17, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.MULT, new Value.RunVar(Value.Var.OPP_CARDS_SOLD, 0, 4))))),

                // --- batch 38: blind-skipping (Throwback) ---
                new JokerDef("j_throwback", "Throwback", "x0.25 Mult for each blind skipped this run",
                        "Uncommon", 6, 9, 16, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT, new Value.RunVar(Value.Var.BLINDS_SKIPPED, 1, 0.25))))),

                // --- batch 37: "since acquired" jokers via acquire-stamp (Turtle Bean, Seltzer) ---
                new JokerDef("j_turtle_bean", "Turtle Bean",
                        "+5 hand size, which decreases by 1 each round (handled in Run.applyJokerRunMods)",
                        "Uncommon", 6, 7, 16, null, null, true, List.of(), List.of()),
                new JokerDef("j_seltzer", "Seltzer",
                        "Retrigger all played cards for the first 10 hands after it is acquired",
                        "Uncommon", 6, 8, 16, null, null, true, List.of(),
                        List.of(new Rule(Trigger.REPETITION_PLAYED, new Condition.HandsSinceAcquire(10),
                                new EffectTemplate(Op.REPETITIONS, new Value.Const(1))))),

                // --- batch 36: Obelisk (consecutive non-most-played streak; shipped, preview-accurate) ---
                new JokerDef("j_obelisk", "Obelisk",
                        "x0.2 Mult per consecutive hand played that is not your most-played hand",
                        "Legendary", 20, 6, 16, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT, new Value.RunVar(Value.Var.OBELISK_STREAK, 1, 0.2))))),

                // --- batch 35: Loyalty Card (every-6-hands xMult; preview-accurate via shipped counter) ---
                new JokerDef("j_loyalty_card", "Loyalty Card", "x4 Mult every 6 hands played",
                        "Uncommon", 5, 5, 16, null, null, true, List.of(),
                        // hands-played is incremented AFTER scoring, so this hand is play #(total+1);
                        // x4 on the 6th, 12th, ... -> total % 6 == 5. Shipped counter -> previews exactly.
                        List.of(new Rule(Trigger.JOKER_MAIN,
                                new Condition.RunVarModulo(Value.Var.HANDS_PLAYED_TOTAL, 6, 5),
                                new EffectTemplate(Op.XMULT, new Value.Const(4))))),

                // --- batch 34: Trading Card (discard-destroy for money) ---
                new JokerDef("j_trading", "Trading Card",
                        "If the first discard of a round is a single card, destroy it and earn $3",
                        "Uncommon", 6, 4, 16, null, null, true, List.of(), List.of()),

                // --- batch 33: shop-exit / sell-self lifecycle (Perkeo, Invisible, Luchador) ---
                new JokerDef("j_perkeo", "Perkeo",
                        "Creates a copy of a random held consumable when you leave the shop",
                        "Legendary", 20, 1, 16, null, null, true, List.of(), List.of()),
                new JokerDef("j_invisible", "Invisible Joker",
                        "After 2 rounds, sell this to create a copy of a random Joker",
                        "Rare", 8, 2, 16, null, null, true,
                        List.of(new Mutation(Trigger.END_OF_ROUND, new Condition.Always(),
                                "rounds", Mutation.Op.ADD, 1)),
                        List.of()),
                new JokerDef("j_luchador", "Luchador", "Sell this to disable the current Boss Blind",
                        "Uncommon", 5, 3, 16, null, null, true, List.of(), List.of()),

                // --- batch 32: joker-destroyers (Ceremonial Dagger, Madness) ---
                new JokerDef("j_ceremonial", "Ceremonial Dagger",
                        "When a blind is selected, destroys the Joker to the right and gains 2x its sell value as Mult",
                        "Uncommon", 6, 9, 15, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("mult", 1),
                                new EffectTemplate(Op.MULT, new Value.State("mult", 0, 1))))),
                new JokerDef("j_madness", "Madness",
                        "On Small/Big blind select, gains x0.5 Mult and destroys a random Joker",
                        "Uncommon", 7, 0, 16, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("xm", 0.5),
                                new EffectTemplate(Op.XMULT, new Value.State("xm", 1.0, 1.0))))),

                // --- batch 31: Satellite (unique-planet economy) ---
                new JokerDef("j_satellite", "Satellite",
                        "Earn $1 at end of round per unique Planet card used this run",
                        "Uncommon", 6, 8, 15, null, null, true, List.of(),
                        List.of(new Rule(Trigger.END_OF_ROUND, new Condition.Always(),
                                new EffectTemplate(Op.DOLLARS, new Value.RunVar(Value.Var.UNIQUE_PLANETS, 0, 1))))),

                // --- batch 30: Oops! All 6s (probability doubler) + Reserved Parking ---
                new JokerDef("j_oops", "Oops! All 6s", "Doubles all listed probabilities",
                        "Uncommon", 4, 6, 15, null, null, true, List.of(), List.of()),
                new JokerDef("j_reserved_parking", "Reserved Parking",
                        "Each face card held in hand has a 1 in 2 chance to give $1",
                        "Common", 5, 7, 15, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_HELD,
                                new Condition.And(List.of(new Condition.ScoredIsFace(),
                                        new Condition.Chance(1, 2, "reserved_parking"))),
                                new EffectTemplate(Op.DOLLARS, new Value.Const(1))))),

                // --- batch 29: boss-ability disable (Chicot) ---
                new JokerDef("j_chicot", "Chicot", "Disables the effect of every Boss Blind",
                        "Legendary", 20, 5, 15, null, null, true, List.of(), List.of()),

                // --- batch 28: sell-value bonus (Egg, Gift Card) ---
                new JokerDef("j_egg", "Egg", "Gains $3 of sell value at the end of each round",
                        "Common", 4, 3, 15, null, null, true,
                        List.of(new Mutation(Trigger.END_OF_ROUND, new Condition.Always(),
                                "sellBonus", Mutation.Op.ADD, 3)),
                        List.of()),
                new JokerDef("j_gift_card", "Gift Card",
                        "Adds $1 of sell value to every owned Joker at end of round",
                        "Uncommon", 6, 4, 15, null, null, true,
                        List.of(new Mutation(Trigger.END_OF_ROUND, new Condition.Always(),
                                "sellBonus", Mutation.Op.ADD, 1, Mutation.Scope.ALL_JOKERS)),
                        List.of()),

                // --- batch 27: shop/economy hooks (Credit Card, Chaos, Astronomer) ---
                new JokerDef("j_credit_card", "Credit Card", "Go up to -$20 in debt",
                        "Common", 1, 0, 15, null, null, true, List.of(), List.of()),
                new JokerDef("j_chaos", "Chaos the Clown", "1 free reroll each shop",
                        "Common", 4, 1, 15, null, null, true, List.of(), List.of()),
                new JokerDef("j_astronomer", "Astronomer", "All Planet cards in the shop are free",
                        "Uncommon", 8, 2, 15, null, null, true, List.of(), List.of()),

                // --- batch 26: Run-level hooks (Mr Bones death-save, To the Moon interest) ---
                new JokerDef("j_mr_bones", "Mr. Bones",
                        "Prevents death if at least 25% of the required score was reached (then self-destructs)",
                        "Uncommon", 5, 8, 14, null, null, true, List.of(), List.of()),
                new JokerDef("j_to_the_moon", "To the Moon",
                        "Earn an extra $1 of interest per $5 at end of round",
                        "Uncommon", 5, 9, 14, null, null, true, List.of(), List.of()),

                // --- batch 25: Mail-In Rebate (event-count money) ---
                new JokerDef("j_mail_in_rebate", "Mail-In Rebate",
                        "Earn $3 for each discarded card of this round's rank",
                        "Common", 4, 7, 14, null, null, true, List.of(),
                        List.of(new Rule(Trigger.PRE_DISCARD, new Condition.Always(),
                                new EffectTemplate(Op.DOLLARS, new Value.Count(Value.Source.EVENT,
                                        new Condition.ScoredRankIsTarget("rebateRankId"), 0, 3))))),

                // --- batch 24: more dynamic targets (Castle chips, To Do List money) ---
                new JokerDef("j_castle", "Castle",
                        "Gains +3 Chips per discarded card of this round's suit",
                        "Uncommon", 6, 5, 14, null, null, true,
                        List.of(new Mutation(Trigger.BLIND_SELECTED, new Condition.Always(),
                                        "chips", Mutation.Op.RESET, 0),
                                new Mutation(Trigger.PRE_DISCARD, new Condition.Always(), "chips",
                                        Mutation.Op.ADD, 3, new Condition.ScoredSuitIsTarget("castleSuit"))),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("chips", 1),
                                new EffectTemplate(Op.CHIPS, new Value.State("chips", 0, 1))))),
                new JokerDef("j_todo_list", "To Do List",
                        "Earn $4 if the played poker hand is this round's hand",
                        "Common", 4, 6, 14, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.HandIsTarget("todoHand"),
                                new EffectTemplate(Op.DOLLARS, new Value.Const(4))))),

                // --- batch 23: per-round dynamic targets (The Idol, Ancient Joker) ---
                new JokerDef("j_idol", "The Idol",
                        "Each played card matching this round's Idol card gives x2 Mult",
                        "Uncommon", 6, 3, 14, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.And(List.of(
                                        new Condition.ScoredRankIsTarget("idolRankId"),
                                        new Condition.ScoredSuitIsTarget("idolSuit"))),
                                new EffectTemplate(Op.XMULT, new Value.Const(2))))),
                new JokerDef("j_ancient_joker", "Ancient Joker",
                        "Each played card of this round's Ancient suit gives x1.5 Mult",
                        "Rare", 8, 4, 14, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredSuitIsTarget("ancientSuit"),
                                new EffectTemplate(Op.XMULT, new Value.Const(1.5))))),

                // --- batch 22: joker-on-joker reads (Baseball Card, Swashbuckler) ---
                new JokerDef("j_baseball_card", "Baseball Card", "Each Uncommon Joker gives x1.5 Mult",
                        "Rare", 8, 1, 14, null, null, true, List.of(),
                        List.of(new Rule(Trigger.ON_OTHER_JOKER, new Condition.OtherJokerRarity("Uncommon"),
                                new EffectTemplate(Op.XMULT, new Value.Const(1.5))))),
                new JokerDef("j_swashbuckler", "Swashbuckler",
                        "Adds the sell value of all other owned Jokers to Mult",
                        "Common", 4, 2, 14, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.MULT, new Value.OtherJokersSellSum(0, 1))))),

                // --- batch 21: per-hand-type play tracking (Supernova, Card Sharp) ---
                new JokerDef("j_supernova", "Supernova",
                        "Adds the number of times the played hand has been played this run to Mult",
                        "Common", 5, 8, 13, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.MULT, new Value.HandTypePlays(1, 1))))),
                new JokerDef("j_card_sharp", "Card Sharp",
                        "x3 Mult if the played poker hand was already played this round",
                        "Uncommon", 6, 9, 13, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.HandPlayedThisRound(),
                                new EffectTemplate(Op.XMULT, new Value.Const(3))))),

                // --- batch 20: Lucky Cat (counts Lucky triggers) ---
                new JokerDef("j_lucky_cat", "Lucky Cat",
                        "Gains x0.25 Mult each time a Lucky card successfully triggers",
                        "Uncommon", 6, 6, 13, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT,
                                        new Value.RunVar(Value.Var.LUCKY_TRIGGERS, 1, 0.25))))),

                // --- batch 19: sell action (Campfire) ---
                new JokerDef("j_campfire", "Campfire",
                        "Gains x0.25 Mult per card sold; resets when a Boss Blind is defeated",
                        "Rare", 9, 5, 13, null, null, true,
                        List.of(new Mutation(Trigger.SELL_CARD, new Condition.Always(),
                                        "x", Mutation.Op.ADD, 0.25),
                                new Mutation(Trigger.END_OF_ROUND, new Condition.BossDefeated(),
                                        "x", Mutation.Op.RESET, 0)),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT, new Value.State("x", 1.0, 1.0))))),

                new JokerDef("j_ramen", "Ramen", "x2 Mult, loses x0.01 per card discarded",
                        "Uncommon", 6, 4, 13, null, null, true, List.of(),
                        List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                                new EffectTemplate(Op.XMULT, new Value.Clamp(
                                        new Value.RunVar(Value.Var.CARDS_DISCARDED_TOTAL, 2, -0.01), 1, 1e9))))));
    }

    /** A joker whose only effect is a passive per-blind {@link RunMod}. */
    private static JokerDef runModJoker(String key, String name, String desc, String rarity,
            int cost, int ax, int ay, RunMod mod) {
        return new JokerDef(key, name, desc, rarity, cost, ax, ay, null, null, true,
                List.of(), List.of(), List.of(), mod);
    }
}
