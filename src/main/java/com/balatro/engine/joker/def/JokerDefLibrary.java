package com.balatro.engine.joker.def;

import com.balatro.engine.card.Suit;
import com.balatro.engine.joker.Trigger;
import com.balatro.engine.joker.def.Condition.Cmp;
import com.balatro.engine.joker.def.EffectTemplate.Op;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hand-coded starter jokers, re-expressed as pure {@link JokerDef} data — the
 * proof that the data model is faithful (see {@code DataJokerTest}, which asserts
 * each def reproduces the hand-coded joker's numeric output across every relevant
 * trigger). Every joker in {@code JokerLibrary} except Blueprint (a re-entrant
 * meta-joker, intentionally left native) is covered here. These same building
 * blocks are what the builder UI exposes for authoring brand-new jokers.
 *
 * <p>Keys match the hand-coded jokers so equivalence can be checked one-to-one.
 */
public final class JokerDefLibrary {

    private JokerDefLibrary() {}

    private static final Map<String, JokerDef> DEFS = new LinkedHashMap<>();

    static {
        // joker_main: flat +mult
        put(new JokerDef("j_joker", "Joker", "+4 Mult", "Common", 2, 0, 0, null, null, true,
                List.of(),
                List.of(rule(Trigger.JOKER_MAIN, new Condition.Always(), Op.MULT, 4))));

        // on_scored: per played Diamond, +3 mult
        put(new JokerDef("j_greedy_joker", "Greedy Joker",
                "Each played Diamond gives +3 Mult", "Common", 5, 6, 1, null, null, true,
                List.of(),
                List.of(rule(Trigger.ON_SCORED, new Condition.ScoredSuit(Suit.DIAMONDS), Op.MULT, 3))));

        // joker_main: +50 chips if hand contains a pair
        put(new JokerDef("j_sly_joker", "Sly Joker",
                "+50 Chips if the played hand contains a Pair", "Common", 3, 0, 14, null, null, true,
                List.of(),
                List.of(rule(Trigger.JOKER_MAIN, new Condition.HandContainsPair(), Op.CHIPS, 50))));

        // joker_main: +20 mult if 3 or fewer cards played
        put(new JokerDef("j_half", "Half Joker",
                "+20 Mult if 3 or fewer cards are played", "Common", 5, 7, 0, null, null, true,
                List.of(),
                List.of(rule(Trigger.JOKER_MAIN, new Condition.PlayedCount(Cmp.LTE, 3), Op.MULT, 20))));

        // on_scored: per played even-rank card, +4 mult
        put(new JokerDef("j_even_steven", "Even Steven",
                "Each played even-rank card gives +4 Mult", "Common", 4, 8, 3, null, null, true,
                List.of(),
                List.of(rule(Trigger.ON_SCORED, new Condition.ScoredParity(true), Op.MULT, 4))));

        // stateful: +1 mult per consecutive faceless hand
        put(new JokerDef("j_ride_the_bus", "Ride the Bus",
                "+1 Mult per consecutive hand with no face card", "Common", 6, 1, 6, null, null, true,
                List.of(
                        // streak breaks to 0 on a face card, else advances by 1 (checked at BEFORE)
                        new Mutation(Trigger.BEFORE, new Condition.ScoringAnyFace(), "streak", Mutation.Op.RESET, 0),
                        new Mutation(Trigger.BEFORE, new Condition.Not(new Condition.ScoringAnyFace()), "streak", Mutation.Op.ADD, 1)),
                List.of(new Rule(Trigger.JOKER_MAIN, new Condition.Always(),
                        new EffectTemplate(Op.MULT, new Value.State("streak", 0, 1))))));

        // retrigger: each played 2-5 retriggers once
        put(new JokerDef("j_hack", "Hack",
                "Retrigger each played 2, 3, 4, and 5", "Uncommon", 6, 5, 2, null, null, true,
                List.of(),
                List.of(rule(Trigger.REPETITION_PLAYED, new Condition.ScoredRankBetween(2, 5), Op.REPETITIONS, 1))));

        // lifecycle: +$4 at end of round
        put(new JokerDef("j_golden", "Golden Joker", "+$4 at end of round", "Common", 6, 9, 2, null, null, true,
                List.of(),
                List.of(rule(Trigger.END_OF_ROUND, new Condition.Always(), Op.DOLLARS, 4))));

        // lifecycle: +$5 if 3+ face cards discarded at once
        put(new JokerDef("j_faceless", "Faceless Joker",
                "+$5 if 3 or more face cards are discarded at once", "Common", 4, 1, 11, null, null, true,
                List.of(),
                List.of(rule(Trigger.PRE_DISCARD, new Condition.DiscardedFaceCount(3), Op.DOLLARS, 5))));

        // stateful: gains x0.1 mult per Planet used
        put(new JokerDef("j_constellation", "Constellation",
                "Gains x0.1 Mult per Planet card used", "Uncommon", 6, 9, 10, null, null, true,
                List.of(new Mutation(Trigger.USE_CONSUMABLE, new Condition.ConsumableType("Planet"),
                        "planets", Mutation.Op.ADD, 1)),
                List.of(new Rule(Trigger.JOKER_MAIN, new Condition.StateAtLeast("planets", 1),
                        new EffectTemplate(Op.XMULT, new Value.State("planets", 1.0, 0.1))))));
    }

    private static Rule rule(Trigger when, Condition cond, Op op, double amount) {
        return new Rule(when, cond, new EffectTemplate(op, new Value.Const(amount)));
    }

    private static void put(JokerDef def) {
        DEFS.put(def.key(), def);
    }

    public static JokerDef get(String key) {
        return DEFS.get(key);
    }

    public static Map<String, JokerDef> all() {
        return DEFS;
    }
}
