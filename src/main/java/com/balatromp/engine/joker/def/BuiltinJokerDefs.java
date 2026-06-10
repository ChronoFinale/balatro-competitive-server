package com.balatromp.engine.joker.def;

import com.balatromp.engine.card.Suit;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.joker.Trigger;
import com.balatromp.engine.joker.def.Condition.Cmp;
import com.balatromp.engine.joker.def.EffectTemplate.Op;
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

    /** Suit → +3 Mult per played card of that suit (Greedy/Lusty/Wrathful/Gluttonous family). */
    private static JokerDef suitMult(String key, String name, Suit suit, int atlasX, int atlasY) {
        return new JokerDef(key, name, "Each played " + suitName(suit) + " gives +3 Mult",
                "Common", 5, atlasX, atlasY, null, null, true, List.of(),
                List.of(new Rule(Trigger.ON_SCORED, new Condition.ScoredSuit(suit),
                        new EffectTemplate(Op.MULT, new Value.Const(3)))));
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

    public static List<JokerDef> all() {
        return List.of(
                // --- suit-mult family (Greedy is hand-coded; here are the other three) ---
                suitMult("j_lusty_joker", "Lusty Joker", Suit.HEARTS, 7, 1),
                suitMult("j_wrathful_joker", "Wrathful Joker", Suit.SPADES, 8, 1),
                suitMult("j_gluttenous_joker", "Gluttonous Joker", Suit.CLUBS, 9, 1),

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
                                        new EffectTemplate(Op.MULT, new Value.Const(4)))))));
    }
}
