package com.balatro.engine.joker.def;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.Trigger;
import com.balatro.engine.joker.def.Effect.Op;
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
import static com.balatro.engine.joker.def.Cond.runVar;
import static com.balatro.engine.joker.def.Cond.value;
import com.balatro.engine.hand.HandMod;
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
                Jokers.common("j_hanging_chad", "Hanging Chad").cost(4).atlas(1, 5)
                        .desc("Retrigger the first and second played cards 1 additional time each (multiplayer)")
                        .on(Trigger.REPETITION_PLAYED).when(card().amongFirst(2)).retrigger().build(),
                // MP Seltzer: retrigger window is 8 hands (vanilla 10).
                Jokers.uncommon("j_seltzer", "Seltzer").cost(6).atlas(8, 16)
                        .desc("Retrigger all played cards for the first 8 hands after it is acquired (multiplayer)")
                        .on(Trigger.REPETITION_PLAYED).when(Cond.handsSinceAcquired(8)).retrigger().build(),
                // MP Golden Ticket: $3 per scored Gold card (vanilla $4), Uncommon.
                Jokers.uncommon("j_golden_ticket", "Golden Ticket").cost(5).atlas(6, 7)
                        .desc("Played Gold cards give $3 when scored (multiplayer)")
                        .forEachScored(card().enhancement(Enhancement.GOLD)).add(DOLLARS, 3).build()));
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
        return Jokers.common(key, name).cost(4).atlas(ax, ay)
                .desc("+" + mult + " Mult if the played hand contains a " + part.display)
                .whenHand(playedHand().contains(part)).add(MULT, mult)
                .build();
    }

    /** "+N Chips if the played hand contains [type]" (Sly/Wily/Clever/Devious/Crafty family). */
    private static JokerDef typeChips(String key, String name, HandType part, int chips, int ax, int ay) {
        return Jokers.common(key, name).cost(4).atlas(ax, ay)
                .desc("+" + chips + " Chips if the played hand contains a " + part.display)
                .whenHand(playedHand().contains(part)).add(CHIPS, chips)
                .build();
    }

    /** "xN Mult if the played hand contains [type]" (The Duo/Trio/Family/Order/Tribe). Rare, $8. */
    private static JokerDef typeXMult(String key, String name, HandType part, double xmult, int ax, int ay) {
        return Jokers.rare(key, name).cost(8).atlas(ax, ay)
                .desc("x" + xmult + " Mult if the played hand contains a " + part.display)
                .whenHand(playedHand().contains(part)).multiply(MULT, xmult)
                .build();
    }

    /** "Each played [suit] gives +N Chips/Mult" gem joker (Arrowhead/Onyx Agate). Uncommon, $7. */
    private static JokerDef gem(String key, String name, Suit suit, Op op, int amount, int ax, int ay) {
        String unit = op == Op.CHIPS ? " Chips" : " Mult";
        return Jokers.uncommon(key, name).cost(7).atlas(ax, ay)
                .desc("Each played " + suitName(suit) + " gives +" + amount + unit)
                .forEachScored(card().suit(suit)).gives(op, Val.of(amount))
                .build();
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
                Jokers.common("j_jolly", "Jolly Joker").cost(3).atlas(2, 0)
                        .desc("+8 Mult if the played hand contains a Pair")
                        .whenHand(playedHand().containsPair()).add(MULT, 8).build(),

                Jokers.common("j_mystic_summit", "Mystic Summit").cost(5).atlas(2, 2)
                        .desc("+15 Mult when 0 discards remain")
                        .whenHand(runVar(Value.Var.DISCARDS_LEFT).exactly(0)).add(MULT, 15).build(),

                // --- run-state scaling ---
                Jokers.common("j_banner", "Banner").cost(5).atlas(1, 2)
                        .desc("+30 Chips for each remaining discard")
                        .whenHand().add(CHIPS, Val.runVar(Value.Var.DISCARDS_LEFT, 0, 30)).build(),

                Jokers.uncommon("j_bull", "Bull").cost(6).atlas(7, 14)
                        .desc("+2 Chips for each $1 you have")
                        .whenHand().add(CHIPS, Val.runVar(Value.Var.MONEY, 0, 2)).build(),

                // --- per-card chips ---
                Jokers.common("j_scary_face", "Scary Face").cost(4).atlas(2, 3)
                        .desc("Played face cards give +30 Chips")
                        .forEachScored(card().isFace()).add(CHIPS, 30).build(),

                Jokers.common("j_odd_todd", "Odd Todd").cost(4).atlas(9, 3)
                        .desc("Played odd-rank cards (A,9,7,5,3) give +31 Chips")
                        .forEachScored(card().odd()).add(CHIPS, 31).build(),

                // --- stateful: gains chips as you play exactly-4-card hands ---
                Jokers.common("j_square", "Square Joker").cost(4).atlas(9, 11)
                        .desc("Gains +4 Chips if exactly 4 cards are played")
                        .beforeScoring(playedHand().sizeExactly(4)).gain("chips", 4)
                        .whenHand(state("chips").atLeast(1)).add(CHIPS, Val.state("chips")).build(),

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

                // --- compound effect: Aces give chips AND mult (extra-chain via .effect escape hatch) ---
                Jokers.common("j_scholar", "Scholar").cost(4).atlas(0, 4)
                        .desc("Played Aces give +20 Chips and +4 Mult")
                        .forEachScored(card().rankBetween(14, 14))
                        .effect(new Effect.Score(Op.CHIPS, Val.of(20)), new Effect.Score(Op.MULT, Val.of(4))).build(),

                // --- per-card mult ---
                Jokers.common("j_smiley", "Smiley Face").cost(4).atlas(6, 15)
                        .desc("Played face cards give +5 Mult")
                        .forEachScored(card().isFace()).add(MULT, 5).build(),

                // --- compound per-card: each played 10 or 4 gives +10 Chips and +4 Mult ---
                Jokers.common("j_walkie_talkie", "Walkie Talkie").cost(4).atlas(8, 15)
                        .desc("Each played 10 or 4 gives +10 Chips and +4 Mult")
                        .forEachScored(any(card().rankBetween(10, 10), card().rankBetween(4, 4)))
                        .effect(new Effect.Score(Op.CHIPS, Val.of(10)), new Effect.Score(Op.MULT, Val.of(4))).build(),

                // --- rank-set via any(): each played A, 2, 3, 5, 8 gives +8 Mult ---
                Jokers.uncommon("j_fibonacci", "Fibonacci").cost(8).atlas(1, 5)
                        .desc("Each played Ace, 2, 3, 5, or 8 gives +8 Mult")
                        .forEachScored(any(card().rankBetween(14, 14), card().rankBetween(2, 2),
                                card().rankBetween(3, 3), card().rankBetween(5, 5), card().rankBetween(8, 8)))
                        .add(MULT, 8).build(),

                // --- held-card additive mult: each Queen held gives +13 Mult ---
                Jokers.common("j_shoot_the_moon", "Shoot the Moon").cost(5).atlas(2, 6)
                        .desc("Each Queen held in hand gives +13 Mult")
                        .forEachHeld(card().rankBetween(12, 12)).add(MULT, 13).build(),

                // --- held-card xmult: each King held gives x1.5 Mult ---
                Jokers.rare("j_baron", "Baron").cost(8).atlas(6, 12)
                        .desc("Each King held in hand gives x1.5 Mult")
                        .forEachHeld(card().rankBetween(13, 13)).multiply(MULT, 1.5).build(),

                // --- stateful: gains +15 Chips whenever the played hand contains a Straight ---
                Jokers.common("j_runner", "Runner").cost(5).atlas(3, 10)
                        .desc("Gains +15 Chips if the played hand contains a Straight")
                        .beforeScoring(playedHand().contains(HandType.STRAIGHT)).gain("chips", 15)
                        .whenHand(state("chips").atLeast(1)).add(CHIPS, Val.state("chips")).build(),

                // --- stateful: gains +8 Chips for each played 2 ---
                Jokers.rare("j_wee", "Wee Joker").cost(8).atlas(0, 0)
                        .desc("Gains +8 Chips for each played 2")
                        .mutate(Trigger.ON_SCORED).when(card().rankBetween(2, 2)).gain("chips", 8)
                        .whenHand(state("chips").atLeast(1)).add(CHIPS, Val.state("chips")).build(),

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
                Jokers.uncommon("j_sock_and_buskin", "Sock and Buskin").cost(6).atlas(3, 1)
                        .desc("Retrigger all played face cards")
                        .retriggerEachScored(card().isFace()).build(),

                // --- stateful: gains +2 Mult per shop reroll ---
                Jokers.uncommon("j_flash", "Flash Card").cost(5).atlas(0, 15)
                        .desc("Gains +2 Mult per shop reroll")
                        .mutate(Trigger.REROLL_SHOP).when(always()).gain("mult", 2)
                        .whenHand(state("mult").atLeast(1)).add(MULT, Val.state("mult")).build(),

                // --- stateful: gains +2 Mult whenever the played hand contains a Two Pair ---
                Jokers.uncommon("j_trousers", "Spare Trousers").cost(6).atlas(4, 15)
                        .desc("Gains +2 Mult if the played hand contains a Two Pair")
                        .beforeScoring(playedHand().contains(HandType.TWO_PAIR)).gain("mult", 2)
                        .whenHand(state("mult").atLeast(1)).add(MULT, Val.state("mult")).build(),

                // --- probabilistic (Chance / Random) ---
                Jokers.common("j_misprint", "Misprint").cost(4).atlas(6, 2)
                        .desc("Adds +0 to +23 Mult (random each hand)")
                        .whenHand().add(MULT, Val.random(0, 23, "misprint")).build(),
                Jokers.uncommon("j_bloodstone", "Bloodstone").cost(7).atlas(0, 8)
                        .desc("1 in 2 chance each played Heart gives x1.5 Mult")
                        .forEachScored(Cond.all(card().suit(Suit.HEARTS), Cond.chance(1, 2, "bloodstone")))
                        .multiply(MULT, 1.5).build(),

                // --- deck/run-stat scaling (Value.Stat) ---
                Jokers.common("j_blue_joker", "Blue Joker").cost(5).atlas(7, 10)
                        .desc("+2 Chips for each card remaining in the deck")
                        .whenHand().add(CHIPS, Val.stat(Value.Which.DECK_REMAINING, 0, 2, null)).build(),
                Jokers.common("j_abstract", "Abstract Joker").cost(4).atlas(3, 3)
                        .desc("+3 Mult for each Joker")
                        .whenHand().add(MULT, Val.stat(Value.Which.OWNED_JOKERS, 0, 3, null)).build(),
                Jokers.uncommon("j_stone", "Stone Joker").cost(6).atlas(9, 0)
                        .desc("+25 Chips for each Stone card in the deck")
                        .whenHand().add(CHIPS, Val.stat(Value.Which.DECK_ENH_COUNT, 0, 25, Enhancement.STONE)).build(),
                Jokers.uncommon("j_steel_joker", "Steel Joker").cost(7).atlas(7, 2)
                        .desc("x0.2 Mult for each Steel card in the deck")
                        .whenHand().multiply(MULT, Val.stat(Value.Which.DECK_ENH_COUNT, 1.0, 0.2, Enhancement.STEEL)).build(),

                // --- MUTATE_CARD: Hiker permanently adds chips to each played card ---
                Jokers.uncommon("j_hiker", "Hiker").cost(5).atlas(0, 11)
                        .desc("Each played card permanently gains +5 Chips")
                        .forEachScored(always()).mutateCard(CardMod.addChips(5)).build(),

                // --- MUTATE_CARD: Midas Mask turns played face cards into Gold cards ---
                Jokers.uncommon("j_midas_mask", "Midas Mask").cost(7).atlas(0, 13)
                        .desc("Played face cards become Gold cards when scored")
                        .forEachScored(card().isFace()).mutateCard(CardMod.setEnhancement(Enhancement.GOLD)).build(),

                // --- MUTATE_CARD + stateful xMult: Vampire strips enhancements and grows ---
                Jokers.uncommon("j_vampire", "Vampire").cost(7).atlas(2, 12)
                        .desc("Gains x0.1 Mult per enhanced card played, removing its enhancement")
                        .mutate(Trigger.ON_SCORED).when(not(card().enhancement(Enhancement.NONE))).gain("xm", 0.1)
                        .forEachScored(not(card().enhancement(Enhancement.NONE)))
                        .mutateCard(CardMod.removeEnhancement())
                        .whenHand(state("xm").atLeast(0.1)).multiply(MULT, Val.state("xm", 1.0, 1.0)).build(),

                // --- economy-during-scoring: money earned mid-hand (credited at end) ---
                Jokers.uncommon("j_rough_gem", "Rough Gem").cost(7).atlas(5, 9)
                        .desc("Each played Diamond gives $1 when scored")
                        .forEachScored(card().suit(Suit.DIAMONDS)).add(DOLLARS, 1).build(),
                Jokers.common("j_business_card", "Business Card").cost(4).atlas(1, 9)
                        .desc("Played face cards have a 1 in 2 chance to give $2")
                        .forEachScored(Cond.all(card().isFace(), Cond.chance(1, 2, "business_card")))
                        .add(DOLLARS, 2).build(),

                // --- flat / xMult jokers expressible with the existing algebra ---
                Jokers.common("j_gros_michel", "Gros Michel").cost(5).atlas(8, 2)
                        .desc("+15 Mult").whenHand().add(MULT, 15).build(),
                Jokers.common("j_cavendish", "Cavendish").cost(5).atlas(9, 2)
                        .desc("x3 Mult").whenHand().multiply(MULT, 3).build(),
                Jokers.uncommon("j_acrobat", "Acrobat").cost(7).atlas(8, 3)
                        .desc("x3 Mult on the final hand of the round")
                        .whenHand(runVar(Value.Var.HANDS_LEFT).atMost(1)).multiply(MULT, 3).build(),
                Jokers.uncommon("j_joker_stencil", "Joker Stencil").cost(7).atlas(0, 4)
                        .desc("x1 Mult for each empty Joker slot (Joker Stencil included)")
                        .whenHand().multiply(MULT, Val.stat(Value.Which.EMPTY_JOKER_SLOTS, 1.0, 1.0, null)).build(),
                Jokers.legendary("j_triboulet", "Triboulet").cost(20).atlas(4, 15)
                        .desc("Played Kings and Queens each give x2 Mult")
                        .forEachScored(card().rankBetween(12, 13)).multiply(MULT, 2).build(),

                // --- ScoredFirst: retrigger the first scored card (Hanging Chad) ---
                Jokers.common("j_hanging_chad", "Hanging Chad").cost(4).atlas(1, 5)
                        .desc("Retrigger the first played card 2 additional times")
                        .on(Trigger.REPETITION_PLAYED).when(card().isFirst())
                        .gives(Op.REPETITIONS, Val.of(2)).build(),

                // --- ScoringContainsSuit: suit-coverage xMult jokers ---
                Jokers.uncommon("j_flower_pot", "Flower Pot").cost(6).atlas(8, 4)
                        .desc("x3 Mult if the scoring hand contains a Diamond, Club, Heart and Spade")
                        .whenHand(Cond.all(playedHand().hasSuit(Suit.DIAMONDS), playedHand().hasSuit(Suit.CLUBS),
                                playedHand().hasSuit(Suit.HEARTS), playedHand().hasSuit(Suit.SPADES)))
                        .multiply(MULT, 3).build(),
                Jokers.uncommon("j_seeing_double", "Seeing Double").cost(6).atlas(9, 4)
                        .desc("x2 Mult if the scoring hand has a Club and a card of any other suit")
                        .whenHand(Cond.all(playedHand().hasSuit(Suit.CLUBS), any(playedHand().hasSuit(Suit.DIAMONDS),
                                playedHand().hasSuit(Suit.HEARTS), playedHand().hasSuit(Suit.SPADES))))
                        .multiply(MULT, 2).build(),

                // --- global hand-evaluation modifiers (HandMod): change what hands form ---
                Jokers.uncommon("j_four_fingers", "Four Fingers").cost(7).atlas(1, 6)
                        .desc("All Flushes and Straights can be made with 4 cards")
                        .handMod(HandMod.FOUR_FINGERS).build(),
                Jokers.uncommon("j_shortcut", "Shortcut").cost(7).atlas(2, 6)
                        .desc("Allows Straights to be made with gaps of 1 rank")
                        .handMod(HandMod.SHORTCUT).build(),
                Jokers.uncommon("j_smeared_joker", "Smeared Joker").cost(7).atlas(3, 6)
                        .desc("Hearts and Diamonds count as the same suit, Spades and Clubs the same")
                        .handMod(HandMod.SMEARED).build(),

                // --- retrigger jokers (existing algebra) ---
                Jokers.uncommon("j_mime", "Mime").cost(5).atlas(8, 5)
                        .desc("Retrigger all cards held in hand")
                        .on(Trigger.REPETITION_HELD).retrigger().build(),
                Jokers.uncommon("j_dusk", "Dusk").cost(5).atlas(9, 5)
                        .desc("Retrigger all played cards on the final hand of the round")
                        .on(Trigger.REPETITION_PLAYED).when(runVar(Value.Var.HANDS_LEFT).atMost(1)).retrigger().build(),

                // --- Gold-card economy when scored ---
                Jokers.common("j_golden_ticket", "Golden Ticket").cost(5).atlas(6, 7)
                        .desc("Played Gold cards give $4 when scored")
                        .forEachScored(card().enhancement(Enhancement.GOLD)).add(DOLLARS, 4).build(),

                // --- batch 2: first-face / stat-threshold / held-suit conditions ---
                Jokers.common("j_photograph", "Photograph").cost(5).atlas(4, 7)
                        .desc("The first scored face card gives x2 Mult")
                        .forEachScored(card().firstFace()).multiply(MULT, 2).build(),
                Jokers.rare("j_drivers_license", "Driver's License").cost(7).atlas(6, 13)
                        .desc("x3 Mult if you have at least 16 enhanced cards in your deck")
                        .whenHand(value(Val.stat(Value.Which.ENHANCED_CARD_COUNT, 0, 1, null)).atLeast(16))
                        .multiply(MULT, 3).build(),
                Jokers.uncommon("j_blackboard", "Blackboard").cost(6).atlas(0, 7)
                        .desc("x3 Mult if all cards held in hand are Spades or Clubs")
                        .whenHand(held().allSuits(Suit.SPADES, Suit.CLUBS)).multiply(MULT, 3).build(),

                // --- batch 3: global scoring modifiers ---
                Jokers.uncommon("j_pareidolia", "Pareidolia").cost(5).atlas(5, 6)
                        .desc("All cards are considered face cards").handMod(HandMod.PAREIDOLIA).build(),
                Jokers.common("j_splash", "Splash").cost(3).atlas(6, 6)
                        .desc("Every played card counts in scoring").handMod(HandMod.SPLASH).build(),

                // --- batch 4: lifecycle counter-scaling (state ships to client, JOKER_MAIN reads it) ---
                Jokers.common("j_green_joker", "Green Joker").cost(4).atlas(6, 5)
                        .desc("+1 Mult per hand played, -1 Mult per discard")
                        .beforeScoring(always()).gain("m", 1)
                        .mutate(Trigger.PRE_DISCARD).when(always()).gain("m", -1)
                        .whenHand(state("m").atLeast(1)).add(MULT, Val.state("m")).build(),
                Jokers.common("j_fortune_teller", "Fortune Teller").cost(6).atlas(8, 8)
                        .desc("+1 Mult per Tarot card used this run")
                        .whenUsing("Tarot").gain("tarots", 1)
                        .whenHand(state("tarots").atLeast(1)).add(MULT, Val.state("tarots")).build(),

                // --- batch 5: stepwise / deck-size scaling values ---
                Jokers.uncommon("j_bootstraps", "Bootstraps").cost(6).atlas(9, 8)
                        .desc("+2 Mult for every $5 you have")
                        .whenHand().add(MULT, Val.runVarStep(Value.Var.MONEY, 0, 2, 5)).build(),
                Jokers.uncommon("j_erosion", "Erosion").cost(6).atlas(7, 8)
                        .desc("+4 Mult for each card below 52 in your full deck")
                        .whenHand().add(MULT, Val.stat(Value.Which.CARDS_BELOW_FULL, 0, 4, null)).build(),

                // --- batch 6: passive standing modifiers (folded at blind start, like deck/voucher mods) ---
                modJoker("j_juggler", "Juggler", "+1 hand size", "Common", 4, 0, 9,
                        Modify.add(Value.Var.HAND_SIZE, 1)),
                modJoker("j_drunkard", "Drunkard", "+1 discard each round", "Common", 4, 1, 10,
                        Modify.add(Value.Var.DISCARDS_LEFT, 1)),
                modJoker("j_troubadour", "Troubadour", "+2 hand size, -1 hand each round", "Uncommon", 6, 2, 10,
                        Modify.add(Value.Var.HANDS_LEFT, -1), Modify.add(Value.Var.HAND_SIZE, 2)),
                modJoker("j_merry_andy", "Merry Andy", "+1 discard, -1 hand size", "Uncommon", 7, 3, 10,
                        Modify.add(Value.Var.DISCARDS_LEFT, 1), Modify.add(Value.Var.HAND_SIZE, -1)),
                Jokers.uncommon("j_burglar", "Burglar").cost(6).atlas(4, 10)
                        .desc("+3 hands this round, but no discards")
                        .mods(Modify.add(Value.Var.HANDS_LEFT, 3)).runMod(RunMod.locksDiscards()).build(),
                Jokers.rare("j_stuntman", "Stuntman").cost(7).atlas(5, 10)
                        .desc("+250 Chips, -2 hand size")
                        .whenHand().add(CHIPS, 250)
                        .mods(Modify.add(Value.Var.HAND_SIZE, -2)).build(),

                // --- batch 7: held-card extreme value ---
                Jokers.common("j_raised_fist", "Raised Fist").cost(5).atlas(7, 9)
                        .desc("Adds double the rank of the lowest card held in hand to Mult")
                        .whenHand().add(MULT, Val.lowestHeld(0, 2)).build(),

                // --- batch 8: end-of-round economy (END_OF_ROUND credits DOLLARS) ---
                Jokers.uncommon("j_cloud_9", "Cloud 9").cost(7).atlas(1, 11)
                        .desc("Earn $1 for each 9 in your full deck at end of round")
                        .atEndOfRound().add(DOLLARS, Val.deckRankCount(9, 0, 1)).build(),
                Jokers.uncommon("j_rocket", "Rocket").cost(6).atlas(2, 11)
                        .desc("Earn $1 at end of round; payout grows by $2 each Boss defeated")
                        .mutate(Trigger.END_OF_ROUND).when(Cond.bossDefeated()).gain("bosses", 1)
                        .atEndOfRound().add(DOLLARS, Val.state("bosses", 1, 2)).build(),
                Jokers.common("j_delayed_gratification", "Delayed Gratification").cost(4).atlas(3, 11)
                        .desc("Earn $2 per remaining discard at end of round if no discards were used")
                        .atEndOfRound().when(not(runVar(Value.Var.DISCARDS_USED).atLeast(1)))
                        .add(DOLLARS, Val.runVar(Value.Var.DISCARDS_LEFT, 0, 2)).build(),

                // --- batch 9: consumable creation ---
                Jokers.common("j_8_ball", "8 Ball").cost(5).atlas(1, 12)
                        .desc("1 in 4 chance for each played 8 to create a Tarot card (if room)")
                        .forEachScored(Cond.all(card().rankBetween(8, 8), Cond.chance(1, 4, "8ball")))
                        .create(CreateSpec.Kind.TAROT).build(),
                Jokers.uncommon("j_cartomancer", "Cartomancer").cost(6).atlas(2, 12)
                        .desc("Create a Tarot card when a blind is selected")
                        .on(Trigger.BLIND_SELECTED).create(CreateSpec.Kind.TAROT).build(),
                Jokers.rare("j_vagabond", "Vagabond").cost(8).atlas(3, 12)
                        .desc("Create a Tarot if a hand is played with $4 or less")
                        .whenHand(not(runVar(Value.Var.MONEY).atLeast(5)))
                        .create(CreateSpec.Kind.TAROT).build(),
                Jokers.common("j_superposition", "Superposition").cost(5).atlas(4, 12)
                        .desc("Create a Tarot if a played hand contains an Ace and a Straight")
                        .whenHand(Cond.all(playedHand().contains(HandType.STRAIGHT),
                                value(Val.count(Value.Source.SCORING,
                                        card().rankBetween(14, 14), 0, 1)).atLeast(1)))
                        .create(CreateSpec.Kind.TAROT).build(),
                Jokers.uncommon("j_seance", "Seance").cost(6).atlas(5, 12)
                        .desc("Create a Spectral if the played hand is a Straight Flush")
                        .whenHand(playedHand().is(HandType.STRAIGHT_FLUSH))
                        .create(CreateSpec.Kind.SPECTRAL).build(),

                // --- batch 11: joker / playing-card creation + card destruction ---
                Jokers.uncommon("j_marble", "Marble Joker").cost(6).atlas(6, 12)
                        .desc("Adds a Stone card to your deck when a blind is selected")
                        .on(Trigger.BLIND_SELECTED)
                        .create(new CreateSpec(CreateSpec.Kind.PLAYING_CARD, 1, null, Enhancement.STONE)).build(),
                Jokers.common("j_riff_raff", "Riff-Raff").cost(6).atlas(7, 12)
                        .desc("Create 2 Common jokers when a blind is selected (if room)")
                        .on(Trigger.BLIND_SELECTED)
                        .create(new CreateSpec(CreateSpec.Kind.JOKER, 2, "Common", null)).build(),
                Jokers.uncommon("j_sixth_sense", "Sixth Sense").cost(6).atlas(8, 12)
                        .desc("Play a single 6: destroy it and create a Spectral")
                        .forEachScored(Cond.all(playedHand().sizeExactly(1), card().rankBetween(6, 6)))
                        .effect(new Effect.DestroyScored(), new Effect.Create(new CreateSpec(CreateSpec.Kind.SPECTRAL))).build(),

                // --- batch 12: hand level-up ---
                Jokers.uncommon("j_space", "Space Joker").cost(5).atlas(0, 13)
                        .desc("1 in 4 chance to upgrade the level of the played hand")
                        .whenHand(Cond.chance(1, 4, "space")).levelUpHand(1).build(),
                // --- batch 13: destruction-counting xMult (CARD_DESTROYED) ---
                Jokers.uncommon("j_glass_joker", "Glass Joker").cost(6).atlas(5, 13)
                        .desc("Gains x0.75 Mult for every Glass card that is destroyed")
                        .whenCardDestroyed(card().enhancement(Enhancement.GLASS)).gain("x", 0.75)
                        .whenHand().multiply(MULT, Val.state("x", 1.0, 1.0)).build(),
                Jokers.legendary("j_canio", "Canio").cost(20).atlas(6, 15)
                        .desc("Gains x1 Mult when a face card is destroyed")
                        .whenCardDestroyed(card().isFace()).gain("x", 1)
                        .whenHand().multiply(MULT, Val.state("x", 1.0, 1.0)).build(),
                Jokers.legendary("j_yorick", "Yorick").cost(20).atlas(7, 15)
                        .desc("Gains x1 Mult for every 23 cards discarded")
                        .mutate(Trigger.PRE_DISCARD).when(always()).gainPerCard("d", 1, always())
                        .whenHand().multiply(MULT, Val.stateStep("d", 1.0, 1.0, 23)).build(),
                Jokers.rare("j_hit_the_road", "Hit the Road").cost(8).atlas(7, 13)
                        .desc("Gains x0.5 Mult for every Jack discarded this round")
                        .mutate(Trigger.BLIND_SELECTED).when(always()).reset("x")
                        // +0.5 per Jack in the discarded set
                        .mutate(Trigger.PRE_DISCARD).when(always()).gainPerCard("x", 0.5, card().rankBetween(11, 11))
                        .whenHand().multiply(MULT, Val.state("x", 1.0, 1.0)).build(),

                // count discards this round (reset at blind select); mutations run before rules,
                // so on the first discard the counter is exactly 1 when the rule is checked.
                Jokers.rare("j_burnt", "Burnt Joker").cost(8).atlas(1, 13)
                        .desc("Upgrade the level of the first discarded poker hand each round")
                        .mutate(Trigger.BLIND_SELECTED).when(always()).reset("discards")
                        .mutate(Trigger.PRE_DISCARD).when(always()).gain("discards", 1)
                        .whenDiscarding(Cond.all(state("discards").atLeast(1), not(state("discards").atLeast(2))))
                        .levelUpHand(1).build(),

                // --- batch 16: card copy (DNA) + sealed card creation (Certificate) ---
                Jokers.rare("j_dna", "DNA").cost(8).atlas(9, 12)
                        .desc("If the first hand of the round is a single card, add a permanent copy to your deck")
                        .forEachScored(Cond.all(playedHand().sizeExactly(1),
                                not(runVar(Value.Var.HANDS_PLAYED).atLeast(1))))
                        .copyScored().build(),
                Jokers.uncommon("j_hologram", "Hologram").cost(7).atlas(8, 13)
                        .desc("Gains x0.25 Mult for every playing card added to your deck")
                        .mutate(Trigger.CARD_ADDED).when(always()).gain("x", 0.25)
                        .whenHand().multiply(MULT, Val.state("x", 1.0, 1.0)).build(),
                Jokers.uncommon("j_certificate", "Certificate").cost(6).atlas(0, 13)
                        .desc("When a blind is selected, add a random playing card with a random seal to your deck")
                        .on(Trigger.BLIND_SELECTED)
                        .create(new CreateSpec(CreateSpec.Kind.PLAYING_CARD, 1, null, null, null, true)).build(),

                // --- batch 18: decay jokers (run-long counters + clamped values) ---
                Jokers.common("j_ice_cream", "Ice Cream").cost(5).atlas(2, 13)
                        .desc("+100 Chips, -5 Chips per hand played")
                        .whenHand().add(CHIPS, Val.clamp(Val.runVar(Value.Var.HANDS_PLAYED_TOTAL, 100, -5), 0, 1e9)).build(),
                Jokers.common("j_popcorn", "Popcorn").cost(5).atlas(3, 13)
                        .desc("+20 Mult, -4 Mult per round played")
                        .whenHand().add(MULT, Val.clamp(Val.runVar(Value.Var.ROUNDS_PLAYED, 20, -4), 0, 1e9)).build(),
                // --- batch 42: Matador (boss-ability interaction) ---
                Jokers.uncommon("j_matador", "Matador").cost(7).atlas(1, 18)
                        .desc("Earn $8 if the played hand triggers the Boss Blind's ability")
                        .whenHand(Cond.bossAbilityActive()).add(DOLLARS, 8).build(),

                // --- batch 41: tags (Diet Cola) ---
                Jokers.uncommon("j_diet_cola", "Diet Cola").cost(6).atlas(0, 18)
                        .desc("Sell this to create a free Double Tag")
                        .runMod(RunMod.createsTagOnSell("tag_double")).build(),

                // --- batch 40: booster packs (Hallucination, Red Card) ---
                Jokers.common("j_hallucination", "Hallucination").cost(4).atlas(1, 17)
                        .desc("1 in 2 chance to create a Tarot card when a booster pack is opened")
                        .on(Trigger.OPEN_BOOSTER).when(Cond.chance(1, 2, "hallucination"))
                        .create(CreateSpec.Kind.TAROT).build(),
                Jokers.common("j_red_card", "Red Card").cost(5).atlas(2, 17)
                        .desc("Gains +3 Mult when a booster pack is skipped")
                        .mutate(Trigger.SKIP_BOOSTER).when(always()).gain("mult", 3)
                        .whenHand(state("mult").atLeast(1)).add(MULT, Val.state("mult")).build(),

                // --- batch 39: Showman (allow duplicate shop offerings; disables the skip-if-owned rule) ---
                Jokers.uncommon("j_showman", "Showman").cost(5).atlas(0, 17)
                        .desc("Joker and Consumable cards may appear multiple times in the shop")
                        .behaviorInCode().build(),

                // --- batch 47: Pizza (consumed at PvP end -> temporary discards) ---
                Jokers.uncommon("j_pizza", "Pizza").cost(5).atlas(0, 18)
                        .desc("At the end of the next PvP blind, consumed for +1 discard to you and +2 to your Nemesis")
                        .behaviorInCode().build(),

                // --- batch 46: Speedrun (reach PvP first -> Spectral; match-coordinated) ---
                Jokers.uncommon("j_speedrun", "Speedrun").cost(6).atlas(9, 17)
                        .desc("If you reach a PvP blind before your Nemesis, create a random Spectral")
                        .behaviorInCode().build(),

                // --- batch 45: Penny Pincher (Nemesis shop-spend economy) ---
                Jokers.uncommon("j_penny_pincher", "Penny Pincher").cost(6).atlas(8, 17)
                        .desc("On entering the shop, gain $1 for every $3 your Nemesis spent last ante")
                        .runMod(RunMod.pvpShopSpendShare(3)).build(),

                // --- batch 44: more Nemesis jokers (Skip-Off, Let's Go Gambling) ---
                Jokers.uncommon("j_skip_off", "Skip-Off").cost(6).atlas(6, 17)
                        .desc("+1 Hand and +1 Discard per additional blind skipped vs your Nemesis")
                        .runMod(RunMod.skipBonus()).build(),
                Jokers.uncommon("j_lets_go_gambling", "Let's Go Gambling").cost(6).atlas(7, 17)
                        .desc("1 in 4 chance for x4 Mult and $10")
                        .whenHand(Cond.chance(1, 4, "gambling"))
                        .effect(new Effect.Score(Op.XMULT, Val.of(4)), new Effect.Score(Op.DOLLARS, Val.of(10))).build(),

                // --- batch 43: multiplayer-exclusive "Nemesis" jokers (read opponent state) ---
                Jokers.uncommon("j_pacifist", "Pacifist").cost(6).atlas(2, 17)
                        .desc("x10 Mult while not in a PvP blind")
                        .whenHand(not(Cond.inPvpBlind())).multiply(MULT, 10).build(),
                Jokers.uncommon("j_defensive_joker", "Defensive Joker").cost(6).atlas(3, 17)
                        .desc("+125 Chips for every life you are behind your Nemesis")
                        .whenHand().add(CHIPS, Val.runVar(Value.Var.OPP_LIVES_BEHIND, 0, 125)).build(),
                Jokers.rare("j_conjoined", "Conjoined Joker").cost(8).atlas(4, 17)
                        .desc("In a PvP blind, x0.5 Mult per hand your Nemesis has left (max x3)")
                        .whenHand(Cond.inPvpBlind())
                        .multiply(MULT, Val.clamp(Val.runVar(Value.Var.OPP_HANDS_LEFT, 1, 0.5), 1, 3)).build(),
                Jokers.uncommon("j_taxes", "Taxes").cost(6).atlas(5, 17)
                        .desc("+4 Mult per card your Nemesis has sold since the last PvP blind")
                        .whenHand().add(MULT, Val.runVar(Value.Var.OPP_CARDS_SOLD, 0, 4)).build(),

                // --- batch 38: blind-skipping (Throwback) ---
                Jokers.uncommon("j_throwback", "Throwback").cost(6).atlas(9, 16)
                        .desc("x0.25 Mult for each blind skipped this run")
                        .whenHand().multiply(MULT, Val.runVar(Value.Var.BLINDS_SKIPPED, 1, 0.25)).build(),

                // --- batch 37: "since acquired" jokers via acquire-stamp (Turtle Bean, Seltzer) ---
                Jokers.uncommon("j_turtle_bean", "Turtle Bean").cost(6).atlas(7, 16)
                        .desc("+5 hand size, which decreases by 1 each round")
                        .runMod(RunMod.decayingHandSize(5)).build(),
                Jokers.uncommon("j_seltzer", "Seltzer").cost(6).atlas(8, 16)
                        .desc("Retrigger all played cards for the first 10 hands after it is acquired")
                        .on(Trigger.REPETITION_PLAYED).when(Cond.handsSinceAcquired(10)).retrigger().build(),

                // --- batch 36: Obelisk (consecutive non-most-played streak; shipped, preview-accurate) ---
                Jokers.legendary("j_obelisk", "Obelisk").cost(20).atlas(6, 16)
                        .desc("x0.2 Mult per consecutive hand played that is not your most-played hand")
                        .whenHand().multiply(MULT, Val.runVar(Value.Var.OBELISK_STREAK, 1, 0.2)).build(),

                // --- batch 35: Loyalty Card (every-6-hands xMult; preview-accurate via shipped counter) ---
                // hands-played is incremented AFTER scoring, so this hand is play #(total+1);
                // x4 on the 6th, 12th, ... -> total % 6 == 5. Shipped counter -> previews exactly.
                Jokers.uncommon("j_loyalty_card", "Loyalty Card").cost(5).atlas(5, 16)
                        .desc("x4 Mult every 6 hands played")
                        .whenHand(Cond.runVarModulo(Value.Var.HANDS_PLAYED_TOTAL, 6, 5)).multiply(MULT, 4).build(),

                // --- batch 34: Trading Card — first single-card discard of the round: destroy it, +$3.
                //     Pure data: PRE_DISCARD, condition (first discard AND one card discarded), then a
                //     compound effect (+$3 and destroy the discarded set). No behaviorInCode. ---
                Jokers.uncommon("j_trading", "Trading Card").cost(6).atlas(4, 16)
                        .desc("If the first discard of a round is a single card, destroy it and earn $3")
                        .whenDiscarding(Cond.all(runVar(Value.Var.DISCARDS_USED).exactly(0),
                                value(Val.count(Value.Source.EVENT, always(), 0, 1)).exactly(1)))
                        .effect(new Effect.Score(Op.DOLLARS, Val.of(3)), new Effect.DestroyDiscarded())
                        .build(),

                // --- batch 33: shop-exit / sell-self lifecycle (Perkeo, Invisible, Luchador) ---
                Jokers.legendary("j_perkeo", "Perkeo").cost(20).atlas(1, 16)
                        .desc("Creates a copy of a random held consumable when you leave the shop")
                        .runMod(RunMod.consumableDuplicator()).build(),
                Jokers.rare("j_invisible", "Invisible Joker").cost(8).atlas(2, 16)
                        .desc("After 2 rounds, sell this to create a copy of a random Joker")
                        .mutate(Trigger.END_OF_ROUND).when(always()).gain("rounds", 1)
                        .runMod(RunMod.duplicatesJokerOnSell(2)).build(),
                Jokers.uncommon("j_luchador", "Luchador").cost(5).atlas(3, 16)
                        .desc("Sell this to disable the current Boss Blind")
                        .runMod(RunMod.disablesBossOnSell()).build(),

                // --- batch 32: joker-destroyers (Ceremonial Dagger, Madness) ---
                Jokers.uncommon("j_ceremonial", "Ceremonial Dagger").cost(6).atlas(9, 15)
                        .desc("When a blind is selected, destroys the Joker to the right and gains 2x its sell value as Mult")
                        .whenHand(state("mult").atLeast(1)).add(MULT, Val.state("mult"))
                        .runMod(RunMod.ceremonialDagger()).build(),
                // x0.5 Mult is a Mutation; eating a random joker is the jokerEater() capability.
                Jokers.uncommon("j_madness", "Madness").cost(7).atlas(0, 16)
                        .desc("On Small/Big blind select, gains x0.5 Mult and destroys a random Joker")
                        .mutate(Trigger.BLIND_SELECTED).when(not(Cond.bossBlind())).gain("xm", 0.5)
                        .whenHand(state("xm").atLeast(0.5)).multiply(MULT, Val.state("xm", 1.0, 1.0))
                        .runMod(RunMod.jokerEater()).build(),

                // --- batch 31: Satellite (unique-planet economy) ---
                Jokers.uncommon("j_satellite", "Satellite").cost(6).atlas(8, 15)
                        .desc("Earn $1 at end of round per unique Planet card used this run")
                        .atEndOfRound().add(DOLLARS, Val.runVar(Value.Var.UNIQUE_PLANETS, 0, 1)).build(),

                // --- batch 30: Oops! All 6s (probability doubler) + Reserved Parking ---
                Jokers.uncommon("j_oops", "Oops! All 6s").cost(4).atlas(6, 15)
                        .desc("Doubles all listed probabilities")
                        .runMod(RunMod.probabilityDoubler()).build(),
                Jokers.common("j_reserved_parking", "Reserved Parking").cost(5).atlas(7, 15)
                        .desc("Each face card held in hand has a 1 in 2 chance to give $1")
                        .forEachHeld(Cond.all(card().isFace(), Cond.chance(1, 2, "reserved_parking"))).add(DOLLARS, 1).build(),

                // --- batch 29: boss-ability disable (Chicot) — a passive capability, expressed as data ---
                runModJoker("j_chicot", "Chicot", "Disables the effect of every Boss Blind",
                        "Legendary", 20, 5, 15, RunMod.bossDisabler()),

                // --- batch 28: sell-value bonus (Egg, Gift Card) ---
                Jokers.common("j_egg", "Egg").cost(4).atlas(3, 15)
                        .desc("Gains $3 of sell value at the end of each round")
                        .mutate(Trigger.END_OF_ROUND).when(always()).gain("sellBonus", 3).build(),
                Jokers.uncommon("j_gift_card", "Gift Card").cost(6).atlas(4, 15)
                        .desc("Adds $1 of sell value to every owned Joker at end of round")
                        .mutate(Trigger.END_OF_ROUND).when(always()).gainEveryJoker("sellBonus", 1).build(),

                // --- batch 27: shop/economy hooks (Credit Card, Chaos, Astronomer) ---
                Jokers.common("j_credit_card", "Credit Card").cost(1).atlas(0, 15)
                        .desc("Go up to -$20 in debt").mods(Modify.min(Value.Var.MIN_MONEY, -20)).build(),
                Jokers.common("j_chaos", "Chaos the Clown").cost(4).atlas(1, 15)
                        .desc("1 free reroll each shop")
                        .mods(Modify.add(Value.Var.FREE_REROLLS, 1)).build(),
                Jokers.uncommon("j_astronomer", "Astronomer").cost(8).atlas(2, 15)
                        .desc("All Planet cards in the shop are free").behaviorInCode().build(),

                // --- batch 26: Run-level hooks (To the Moon interest); Mr Bones is a passive capability ---
                runModJoker("j_mr_bones", "Mr. Bones",
                        "Prevents death if at least 25% of the required score was reached (then self-destructs)",
                        "Uncommon", 5, 8, 14, RunMod.survivesLostBlind(0.25)),
                Jokers.uncommon("j_to_the_moon", "To the Moon").cost(5).atlas(9, 14)
                        .desc("Earn an extra $1 of interest per $5 at end of round").behaviorInCode().build(),

                // --- batch 25: Mail-In Rebate (event-count money) ---
                Jokers.common("j_mail_in_rebate", "Mail-In Rebate").cost(4).atlas(7, 14)
                        .desc("Earn $3 for each discarded card of this round's rank")
                        .whenDiscarding(always()).add(DOLLARS, Val.count(Value.Source.EVENT,
                                card().rankIsTarget("rebateRankId"), 0, 3)).build(),

                // --- batch 24: more dynamic targets (Castle chips, To Do List money) ---
                Jokers.uncommon("j_castle", "Castle").cost(6).atlas(5, 14)
                        .desc("Gains +3 Chips per discarded card of this round's suit")
                        .mutate(Trigger.BLIND_SELECTED).when(always()).reset("chips")
                        .mutate(Trigger.PRE_DISCARD).when(always()).gainPerCard("chips", 3, card().suitIsTarget("castleSuit"))
                        .whenHand(state("chips").atLeast(1)).add(CHIPS, Val.state("chips")).build(),
                Jokers.common("j_todo_list", "To Do List").cost(4).atlas(6, 14)
                        .desc("Earn $4 if the played poker hand is this round's hand")
                        .whenHand(playedHand().isTarget("todoHand")).add(DOLLARS, 4).build(),

                // --- batch 23: per-round dynamic targets (The Idol, Ancient Joker) ---
                Jokers.uncommon("j_idol", "The Idol").cost(6).atlas(3, 14)
                        .desc("Each played card matching this round's Idol card gives x2 Mult")
                        .forEachScored(Cond.all(card().rankIsTarget("idolRankId"),
                                card().suitIsTarget("idolSuit"))).multiply(MULT, 2).build(),
                Jokers.rare("j_ancient_joker", "Ancient Joker").cost(8).atlas(4, 14)
                        .desc("Each played card of this round's Ancient suit gives x1.5 Mult")
                        .forEachScored(card().suitIsTarget("ancientSuit")).multiply(MULT, 1.5).build(),

                // --- batch 22: joker-on-joker reads (Baseball Card, Swashbuckler) ---
                Jokers.rare("j_baseball_card", "Baseball Card").cost(8).atlas(1, 14)
                        .desc("Each Uncommon Joker gives x1.5 Mult")
                        .on(Trigger.ON_OTHER_JOKER).when(Cond.otherJokerRarity("Uncommon")).multiply(MULT, 1.5).build(),
                Jokers.common("j_swashbuckler", "Swashbuckler").cost(4).atlas(2, 14)
                        .desc("Adds the sell value of all other owned Jokers to Mult")
                        .whenHand().add(MULT, Val.otherJokersSellSum(0, 1)).build(),

                // --- batch 21: per-hand-type play tracking (Supernova, Card Sharp) ---
                Jokers.common("j_supernova", "Supernova").cost(5).atlas(8, 13)
                        .desc("Adds the number of times the played hand has been played this run to Mult")
                        .whenHand().add(MULT, Val.handTypePlays(1, 1)).build(),
                Jokers.uncommon("j_card_sharp", "Card Sharp").cost(6).atlas(9, 13)
                        .desc("x3 Mult if the played poker hand was already played this round")
                        .whenHand(playedHand().repeatedThisRound()).multiply(MULT, 3).build(),

                // --- batch 20: Lucky Cat (counts Lucky triggers) ---
                Jokers.uncommon("j_lucky_cat", "Lucky Cat").cost(6).atlas(6, 13)
                        .desc("Gains x0.25 Mult each time a Lucky card successfully triggers")
                        .whenHand().multiply(MULT, Val.runVar(Value.Var.LUCKY_TRIGGERS, 1, 0.25)).build(),

                // --- batch 19: sell action (Campfire) ---
                Jokers.rare("j_campfire", "Campfire").cost(9).atlas(5, 13)
                        .desc("Gains x0.25 Mult per card sold; resets when a Boss Blind is defeated")
                        .mutate(Trigger.SELL_CARD).when(always()).gain("x", 0.25)
                        .mutate(Trigger.END_OF_ROUND).when(Cond.bossDefeated()).reset("x")
                        .whenHand().multiply(MULT, Val.state("x", 1.0, 1.0)).build(),

                Jokers.uncommon("j_ramen", "Ramen").cost(6).atlas(4, 13)
                        .desc("x2 Mult, loses x0.01 per card discarded")
                        .whenHand().multiply(MULT, Val.clamp(
                                Val.runVar(Value.Var.CARDS_DISCARDED_TOTAL, 2, -0.01), 1, 1e9)).build());
    }

    /** A joker whose only effect is a passive per-blind capability {@link RunMod} (Chicot, Mr Bones). */
    private static JokerDef runModJoker(String key, String name, String desc, String rarity,
            int cost, int ax, int ay, RunMod mod) {
        return builderFor(rarity, key, name).cost(cost).atlas(ax, ay).desc(desc).runMod(mod).build();
    }

    /** A joker whose only effect is standing variable {@link Modify}s (Juggler, Drunkard, Troubadour…). */
    private static JokerDef modJoker(String key, String name, String desc, String rarity,
            int cost, int ax, int ay, Modify... mods) {
        return builderFor(rarity, key, name).cost(cost).atlas(ax, ay).desc(desc).mods(mods).build();
    }

    /** The {@link Jokers} factory for a rarity name (the data form carries rarity as a String). */
    private static Jokers builderFor(String rarity, String key, String name) {
        return switch (rarity) {
            case "Common" -> Jokers.common(key, name);
            case "Uncommon" -> Jokers.uncommon(key, name);
            case "Rare" -> Jokers.rare(key, name);
            case "Legendary" -> Jokers.legendary(key, name);
            default -> throw new IllegalArgumentException("unknown rarity: " + rarity);
        };
    }
}
