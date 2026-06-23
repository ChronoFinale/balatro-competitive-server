package com.balatro.engine.joker.def;

import com.balatro.content.jokers.*;

import static com.balatro.engine.joker.def.Cond.card;
import static com.balatro.engine.joker.def.Cond.discard;
import static com.balatro.engine.joker.def.Cond.playedHand;
import static com.balatro.engine.joker.def.Cond.state;
import static com.balatro.engine.joker.def.Target.CHIPS;
import static com.balatro.engine.joker.def.Target.DOLLARS;
import static com.balatro.engine.joker.def.Target.MULT;

import com.balatro.engine.card.Suit;
import com.balatro.engine.joker.Trigger;
import java.util.LinkedHashMap;
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
        put(Jokers.common("j_joker", "Joker").cost(2).atlas(0, 0).desc("+4 Mult")
                .whenHand().add(MULT, 4).build());

        put(Jokers.common("j_greedy_joker", "Greedy Joker").cost(5).atlas(6, 1)
                .desc("Each played Diamond gives +3 Mult")
                .forEachScored(card().suit(Suit.DIAMONDS)).add(MULT, 3).build());

        put(Jokers.common("j_sly_joker", "Sly Joker").cost(3).atlas(0, 14)
                .desc("+50 Chips if the played hand contains a Pair")
                .whenHand(playedHand().containsPair()).add(CHIPS, 50).build());

        put(Jokers.common("j_half", "Half Joker").cost(5).atlas(7, 0)
                .desc("+20 Mult if 3 or fewer cards are played")
                .whenHand(playedHand().sizeAtMost(3)).add(MULT, 20).build());

        put(Jokers.common("j_even_steven", "Even Steven").cost(4).atlas(8, 3)
                .desc("Each played even-rank card gives +4 Mult")
                .forEachScored(card().even()).add(MULT, 4).build());

        // streak breaks to 0 on a face card, else advances by 1 (checked at BEFORE)
        put(Jokers.common("j_ride_the_bus", "Ride the Bus").cost(6).atlas(1, 6)
                .desc("+1 Mult per consecutive hand with no face card")
                .beforeScoring(playedHand().hasFace()).reset("streak")
                .beforeScoring(playedHand().hasNoFace()).gain("streak", 1)
                .whenHand().add(MULT, Val.state("streak")).build());

        put(Jokers.uncommon("j_hack", "Hack").cost(6).atlas(5, 2)
                .desc("Retrigger each played 2, 3, 4, and 5")
                .retriggerEachScored(card().rankBetween(2, 5)).build());

        put(Jokers.common("j_golden", "Golden Joker").cost(6).atlas(9, 2).desc("+$4 at end of round")
                .atEndOfRound().add(DOLLARS, 4).build());

        put(Jokers.common("j_faceless", "Faceless Joker").cost(4).atlas(1, 11)
                .desc("+$5 if 3 or more face cards are discarded at once")
                .whenDiscarding(discard().faces(3)).add(DOLLARS, 5).build());

        put(Jokers.uncommon("j_constellation", "Constellation").cost(6).atlas(9, 10)
                .desc("Gains x0.1 Mult per Planet card used")
                .whenUsing("Planet").gain("planets", 1)
                .whenHand(state("planets").atLeast(1)).multiply(MULT, Val.state("planets", 1.0, 0.1)).build());
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
