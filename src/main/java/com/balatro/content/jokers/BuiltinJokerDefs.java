package com.balatro.content.jokers;

import com.balatro.engine.joker.def.*;
import com.balatro.grammar.*;
import com.balatro.dsl.*;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import com.balatro.grammar.Trigger;
import com.balatro.grammar.Effect.Term;
import static com.balatro.dsl.Cond.always;
import static com.balatro.dsl.Cond.all;
import static com.balatro.dsl.Cond.any;
import static com.balatro.dsl.Cond.not;
import static com.balatro.dsl.Cond.card;
import static com.balatro.dsl.Cond.playedHand;
import static com.balatro.dsl.Cond.held;
import static com.balatro.dsl.Cond.discard;
import static com.balatro.dsl.Cond.using;
import static com.balatro.dsl.Cond.state;
import static com.balatro.dsl.Cond.runVar;
import static com.balatro.dsl.Cond.value;
import com.balatro.engine.hand.HandMod;
import static com.balatro.grammar.Effect.Term.CHIPS;
import static com.balatro.grammar.Effect.Term.DOLLARS;
import static com.balatro.grammar.Effect.Term.MULT;
import java.util.List;

/**
 * Every built-in joker as pure {@link JokerDef} data. A def here declares ONLY its
 * <b>effect</b> (the rules) — separation of concerns: the metadata lives elsewhere,
 * sourced by key. Cost, rarity, and sprite location come from the ground-truth stats
 * table ({@code balatro-joker-stats.json}, gated against game.lua by
 * {@code JokerStatsAuditTest}); the description comes from the localization table
 * ({@code localization/en.json}, via {@link JokerLoc}). So a joker reads as a few lines
 * of declarative rules — no transcribed numbers, no sprite coords, no text in code.
 *
 * <p>Behaviour variants for other rulesets (e.g. the MP reworks) are no longer authored here: a ruleset is a
 * data {@link RulesetOverlay} ({@code /rulesets/*.json}) folded onto this base. Custom JSON jokers carry
 * their own metadata directly.)
 */
public final class BuiltinJokerDefs {

    private BuiltinJokerDefs() {}

    /** Suit → +3 Mult per played card of that suit (Greedy/Lusty/Wrathful/Gluttonous family). */
    /** Skip-Off's amount: how many blinds you've skipped beyond the Nemesis, floored at 0 — a Value, so the
     *  same Modify works as the PvP state moves (clamp(blindsSkipped − opp.blindsSkipped, 0, ∞)). */
    private static Value skipDiff() {
        return Val.floorAt(0, Val.diff(Val.runVar(Value.Var.BLINDS_SKIPPED), Val.runVar(Value.Var.OPP_BLINDS_SKIPPED)));
    }

    /** Turtle Bean's decaying hand-size bonus: {@code max(0, start − roundsPlayed)} — a Value, so the one
     *  Modify decays itself each round (the old acqRounds was always 0, so it's run-level, not per-instance). */
    private static Value turtleBeanDecay(int start) {
        return Val.floorAt(0, Val.diff(Val.of(start), Val.runVar(Value.Var.ROUNDS_PLAYED)));
    }

    private static JokerDef suitMult(String key, String name, Suit suit) {
        // Templated: the suit and the mult are declared PROPERTIES, and the effect is a function over them.
        // Greedy/Lusty/Wrathful/Gluttonous are this one definition with different props.
        return Jokers.of(key, name)
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
    private static JokerDef typeMult(String key, String name, HandType part, int mult) {
        return Jokers.of(key, name)
                .whenHand(playedHand().contains(part)).add(MULT, mult)
                .build();
    }

    /** "+N Chips if the played hand contains [type]" (Sly/Wily/Clever/Devious/Crafty family). */
    private static JokerDef typeChips(String key, String name, HandType part, int chips) {
        return Jokers.of(key, name)
                .whenHand(playedHand().contains(part)).add(CHIPS, chips)
                .build();
    }

    /** "xN Mult if the played hand contains [type]" (The Duo/Trio/Family/Order/Tribe). Rare, $8. */
    private static JokerDef typeXMult(String key, String name, HandType part, double xmult) {
        return Jokers.of(key, name)
                .whenHand(playedHand().contains(part)).multiply(MULT, xmult)
                .build();
    }

    /** "Each played [suit] gives +N Chips/Mult" gem joker (Arrowhead/Onyx Agate). Uncommon, $7. */
    private static JokerDef gem(String key, String name, Suit suit, Term subject, int amount) {
        String unit = subject == Term.CHIPS ? " Chips" : " Mult";
        return Jokers.of(key, name)
                .forEachScored(card().suit(suit)).gives(Effect.Operation.ADD, subject, Val.of(amount))
                .build();
    }

    /**
     * The multiplayer-exclusive "Nemesis" jokers — authored in the DSL here, but <b>not</b> part of the base
     * {@link #all()} (which is pure vanilla). They enter a run only via the MP ruleset overlay's {@code add}
     * list ({@code /rulesets/bmp-0.4.2-ranked.json}, compiled from these defs). Several read opponent state or
     * fire on PvP triggers, so they have no meaning in single-player and never appear in a vanilla pool.
     */
    public static List<JokerDef> mpAdditions() {
        return List.of(
                // Pizza: consumed at PvP end -> temporary discards (mine + the Nemesis's).
                Jokers.of("j_pizza", "Pizza")
                        .on(Trigger.PVP_BLIND_ENDED).effect(
                                new Effect.GrantDiscards(false, 1, 3),  // +1 discard to me, next 3 blinds
                                new Effect.GrantDiscards(true, 2, 3),   // +2 discards to the Nemesis
                                new Effect.Destroy(new Selector.Self()))               // consumed
                        .build(),
                // Speedrun: reach PvP first -> Spectral (match-coordinated).
                Jokers.of("j_speedrun", "Speedrun")
                        .on(Trigger.PVP_BLIND_REACHED).when(Cond.reachedPvpFirst())
                        .create(CreateSpec.Kind.SPECTRAL).build(),
                // Penny Pincher: Nemesis shop-spend economy.
                Jokers.of("j_penny_pincher", "Penny Pincher")
                        .on(Trigger.SHOP_ENTER).effect(new Effect.Write(new Modify(Value.Var.MONEY, Effect.Operation.ADD,
                                new Value.RunVarStep(Value.Var.OPP_SHOP_SPENT, 0, 1, 3)))).build(),
                // Skip-Off / Let's Go Gambling.
                // Skip-Off: +1 Hand and +1 Discard per extra blind skipped vs the Nemesis — a DYNAMIC Modify
                // (the amount is a Value: clamp(blindsSkipped − opp.blindsSkipped, 0, ∞)), not a RunMod.
                Jokers.of("j_skip_off", "Skip-Off")
                        .mods(Modify.add(Hand.PLAYS, skipDiff()), Modify.add(Hand.DISCARDS, skipDiff())).build(),
                Jokers.of("j_lets_go_gambling", "Let's Go Gambling")
                        .whenHand(Cond.chance(1, 4, "gambling"))
                        .effect(Effect.xMult(Val.of(4)), Effect.dollars(Val.of(10))).build(),
                // Opponent-state readers.
                Jokers.of("j_pacifist", "Pacifist")
                        .whenHand(not(Cond.inPvpBlind())).multiply(MULT, 10).build(),
                Jokers.of("j_defensive_joker", "Defensive Joker")
                        .whenHand().add(CHIPS, Val.runVar(Value.Var.OPP_LIVES_BEHIND, 0, 125)).build(),
                Jokers.of("j_conjoined", "Conjoined Joker")
                        .whenHand(Cond.inPvpBlind())
                        .multiply(MULT, Val.clamp(Val.runVar(Value.Var.OPP_HANDS_LEFT, 1, 0.5), 1, 3)).build(),
                Jokers.of("j_taxes", "Taxes")
                        .whenHand().add(MULT, Val.runVar(Value.Var.OPP_CARDS_SOLD, 0, 4)).build());
    }

    public static List<JokerDef> all() {
        return List.of(
                // --- suit-mult family (all four, fully data-driven) ---
                suitMult("j_greedy_joker", "Greedy Joker", Suit.DIAMONDS),
                suitMult("j_lusty_joker", "Lusty Joker", Suit.HEARTS),
                suitMult("j_wrathful_joker", "Wrathful Joker", Suit.SPADES),
                suitMult("j_gluttenous_joker", "Gluttonous Joker", Suit.CLUBS),

                // --- migrated from hand-coded classes: authored fluently — each rule reads as a sentence,
                //     with the quantifier (forEach / whenHand / atEndOfRound) explicit ---
                Jokers.of("j_joker", "Joker")
                        .whenHand().add(MULT, 4).build(),
                Jokers.of("j_sly_joker", "Sly Joker")
                        .whenHand(playedHand().containsPair()).add(CHIPS, 50).build(),
                Jokers.of("j_half", "Half Joker")
                        .whenHand(playedHand().sizeAtMost(3)).add(MULT, 20).build(),
                Jokers.of("j_even_steven", "Even Steven")
                        .forEachScored(card().even()).add(MULT, 4).build(),
                Jokers.of("j_hack", "Hack")
                        .retriggerEachScored(card().rankBetween(2, 5)).build(),
                Jokers.of("j_golden", "Golden Joker")
                        .atEndOfRound().add(DOLLARS, 4).build(),
                Jokers.of("j_faceless", "Faceless Joker")
                        .whenDiscarding(discard().faces(3)).add(DOLLARS, 5).build(),
                Jokers.of("j_constellation", "Constellation").counters("planets")
                        .whenUsing("Planet").gain("planets", 1)
                        .whenHand(state("planets").atLeast(1))
                        .multiply(MULT, Val.xPerState("planets", 0.1)).build(),
                // Ride the Bus — a STATEFUL RESET: streak breaks to 0 on a scoring face, else +1, then +streak Mult.
                // The last hand-coded joker, now data too (mutations only fire at blueprintDepth 0, as before).
                Jokers.of("j_ride_the_bus", "Ride the Bus").counters("streak")
                        .beforeScoring(playedHand().hasFace()).reset("streak")
                        .beforeScoring(playedHand().hasNoFace()).gain("streak", 1)
                        .whenHand().add(MULT, Val.state("streak")).build(),

                // --- meta copiers: the higher-order Copy primitive, now data too. They are themselves
                //     COPYABLE (blueprint_compat=true in real Balatro, game.lua:498/514) — that is what lets
                //     Blueprint copy a Blueprint and chain; the depth guard stops cycles. ---
                Jokers.of("j_blueprint", "Blueprint")
                        .copies(CopySpec.Selector.RIGHT_NEIGHBOR).build(),
                Jokers.of("j_brainstorm", "Brainstorm")
                        .copies(CopySpec.Selector.LEFTMOST).build(),

                // --- type / conditional ---
                Jokers.of("j_jolly", "Jolly Joker")
                        .whenHand(playedHand().containsPair()).add(MULT, 8).build(),

                Jokers.of("j_mystic_summit", "Mystic Summit")
                        .whenHand(runVar(Hand.DISCARDS).exactly(0)).add(MULT, 15).build(),

                // --- run-state scaling ---
                Jokers.of("j_banner", "Banner")
                        .whenHand().add(CHIPS, Val.runVar(Hand.DISCARDS, 0, 30)).build(),

                Jokers.of("j_bull", "Bull")
                        .whenHand().add(CHIPS, Val.runVar(Value.Var.MONEY, 0, 2)).build(),

                // --- per-card chips ---
                Jokers.of("j_scary_face", "Scary Face")
                        .forEachScored(card().isFace()).add(CHIPS, 30).build(),

                Jokers.of("j_odd_todd", "Odd Todd")
                        .forEachScored(card().odd()).add(CHIPS, 31).build(),

                // --- stateful: gains chips as you play exactly-4-card hands ---
                Jokers.of("j_square", "Square Joker").counters("chips")
                        .beforeScoring(playedHand().sizeExactly(4)).gain("chips", 4)
                        .whenHand(state("chips").atLeast(1)).add(CHIPS, Val.state("chips")).build(),

                // --- type-mult family (contains a hand category -> +Mult) ---
                typeMult("j_zany", "Zany Joker", HandType.THREE_OF_A_KIND, 12),
                typeMult("j_mad", "Mad Joker", HandType.TWO_PAIR, 10),
                typeMult("j_crazy", "Crazy Joker", HandType.STRAIGHT, 12),
                typeMult("j_droll", "Droll Joker", HandType.FLUSH, 10),

                // --- type-chips family (contains a hand category -> +Chips) ---
                typeChips("j_wily", "Wily Joker", HandType.THREE_OF_A_KIND, 100),
                typeChips("j_clever", "Clever Joker", HandType.TWO_PAIR, 80),
                typeChips("j_devious", "Devious Joker", HandType.STRAIGHT, 100),
                typeChips("j_crafty", "Crafty Joker", HandType.FLUSH, 80),

                // --- compound effect: Aces give chips AND mult (extra-chain via .effect escape hatch) ---
                Jokers.of("j_scholar", "Scholar")
                        .forEachScored(card().rankBetween(14, 14))
                        .effect(Effect.chips(Val.of(20)), Effect.mult(Val.of(4))).build(),

                // --- per-card mult ---
                Jokers.of("j_smiley", "Smiley Face")
                        .forEachScored(card().isFace()).add(MULT, 5).build(),

                // --- compound per-card: each played 10 or 4 gives +10 Chips and +4 Mult ---
                Jokers.of("j_walkie_talkie", "Walkie Talkie")
                        .forEachScored(any(card().rankBetween(10, 10), card().rankBetween(4, 4)))
                        .effect(Effect.chips(Val.of(10)), Effect.mult(Val.of(4))).build(),

                // --- rank-set via any(): each played A, 2, 3, 5, 8 gives +8 Mult ---
                Jokers.of("j_fibonacci", "Fibonacci")
                        .forEachScored(any(card().rankBetween(14, 14), card().rankBetween(2, 2),
                                card().rankBetween(3, 3), card().rankBetween(5, 5), card().rankBetween(8, 8)))
                        .add(MULT, 8).build(),

                // --- held-card additive mult: each Queen held gives +13 Mult ---
                Jokers.of("j_shoot_the_moon", "Shoot the Moon")
                        .forEachHeld(card().rankBetween(12, 12)).add(MULT, 13).build(),

                // --- held-card xmult: each King held gives x1.5 Mult ---
                Jokers.of("j_baron", "Baron")
                        .forEachHeld(card().rankBetween(13, 13)).multiply(MULT, 1.5).build(),

                // --- stateful: gains +15 Chips whenever the played hand contains a Straight ---
                Jokers.of("j_runner", "Runner").counters("chips")
                        .beforeScoring(playedHand().contains(HandType.STRAIGHT)).gain("chips", 15)
                        .whenHand(state("chips").atLeast(1)).add(CHIPS, Val.state("chips")).build(),

                // --- stateful: gains +8 Chips for each played 2 ---
                Jokers.of("j_wee", "Wee Joker").counters("chips")
                        .mutate(Trigger.ON_SCORED).when(card().rankBetween(2, 2)).gain("chips", 8)
                        .whenHand(state("chips").atLeast(1)).add(CHIPS, Val.state("chips")).build(),

                // --- xMult "contains" family (The Duo / Trio / Family / Order / Tribe) ---
                typeXMult("j_duo", "The Duo", HandType.PAIR, 2),
                typeXMult("j_trio", "The Trio", HandType.THREE_OF_A_KIND, 3),
                typeXMult("j_family", "The Family", HandType.FOUR_OF_A_KIND, 4),
                typeXMult("j_order", "The Order", HandType.STRAIGHT, 3),
                typeXMult("j_tribe", "The Tribe", HandType.FLUSH, 2),

                // --- gem suit jokers ---
                gem("j_arrowhead", "Arrowhead", Suit.SPADES, Term.CHIPS, 50),
                gem("j_onyx_agate", "Onyx Agate", Suit.CLUBS, Term.MULT, 7),

                // --- retrigger: played face cards trigger again ---
                Jokers.of("j_sock_and_buskin", "Sock and Buskin")
                        .retriggerEachScored(card().isFace()).build(),

                // --- stateful: gains +2 Mult per shop reroll ---
                Jokers.of("j_flash", "Flash Card").counters("mult")
                        .mutate(Trigger.REROLL_SHOP).when(always()).gain("mult", 2)
                        .whenHand(state("mult").atLeast(1)).add(MULT, Val.state("mult")).build(),

                // --- stateful: gains +2 Mult whenever the played hand contains a Two Pair ---
                Jokers.of("j_trousers", "Spare Trousers").counters("mult")
                        .beforeScoring(playedHand().contains(HandType.TWO_PAIR)).gain("mult", 2)
                        .whenHand(state("mult").atLeast(1)).add(MULT, Val.state("mult")).build(),

                // --- probabilistic (Chance / Random) ---
                Jokers.of("j_misprint", "Misprint")
                        .whenHand().add(MULT, Val.random(0, 23, "misprint")).build(),
                Jokers.of("j_bloodstone", "Bloodstone")
                        .forEachScored(Cond.all(card().suit(Suit.HEARTS), Cond.chance(1, 2, "bloodstone")))
                        .multiply(MULT, 1.5).build(),

                // --- deck/run-stat scaling (Value.Stat) ---
                Jokers.of("j_blue_joker", "Blue Joker")
                        .whenHand().add(CHIPS, Val.stat(Value.Which.DECK_REMAINING, 0, 2, null)).build(),
                Jokers.of("j_abstract", "Abstract Joker")
                        .whenHand().add(MULT, Val.stat(Value.Which.OWNED_JOKERS, 0, 3, null)).build(),
                Jokers.of("j_stone", "Stone Joker")
                        .whenHand().add(CHIPS, Val.stat(Value.Which.DECK_ENH_COUNT, 0, 25, Enhancement.STONE)).build(),
                Jokers.of("j_steel_joker", "Steel Joker")
                        .whenHand().multiply(MULT, Val.stat(Value.Which.DECK_ENH_COUNT, 1.0, 0.2, Enhancement.STEEL)).build(),

                // --- MUTATE_CARD: Hiker permanently adds chips to each played card ---
                Jokers.of("j_hiker", "Hiker")
                        .forEachScored(always()).mutateCard(CardMod.addChips(5)).build(),

                // --- MUTATE_CARD: Midas Mask turns played face cards into Gold cards ---
                Jokers.of("j_midas_mask", "Midas Mask")
                        .forEachScored(card().isFace()).mutateCard(CardMod.setEnhancement(Enhancement.GOLD)).build(),

                // --- MUTATE_CARD + stateful xMult: Vampire strips enhancements and grows ---
                Jokers.of("j_vampire", "Vampire").counters("xm")
                        .mutate(Trigger.ON_SCORED).when(not(card().enhancement(Enhancement.NONE))).gain("xm", 0.1)
                        .forEachScored(not(card().enhancement(Enhancement.NONE)))
                        .mutateCard(CardMod.removeEnhancement())
                        .whenHand(state("xm").atLeast(0.1)).multiply(MULT, Val.state("xm", 1.0, 1.0)).build(),

                // --- economy-during-scoring: money earned mid-hand (credited at end) ---
                Jokers.of("j_rough_gem", "Rough Gem")
                        .forEachScored(card().suit(Suit.DIAMONDS)).add(DOLLARS, 1).build(),
                Jokers.of("j_business_card", "Business Card")
                        .forEachScored(Cond.all(card().isFace(), Cond.chance(1, 2, "business_card")))
                        .add(DOLLARS, 2).build(),

                // --- flat / xMult jokers expressible with the existing algebra ---
                Jokers.of("j_gros_michel", "Gros Michel")
                        .whenHand().add(MULT, 15)
                        .on(Trigger.END_OF_ROUND).when(Cond.chance(1, 6, "gros_michel"))
                        .effect(new Effect.Destroy(new Selector.Self())).build(),
                Jokers.of("j_cavendish", "Cavendish")
                        .whenHand().multiply(MULT, 3)
                        .on(Trigger.END_OF_ROUND).when(Cond.chance(1, 1000, "cavendish"))
                        .effect(new Effect.Destroy(new Selector.Self())).build(),
                Jokers.of("j_acrobat", "Acrobat")
                        .whenHand(runVar(Hand.PLAYS).atMost(1)).multiply(MULT, 3).build(),
                Jokers.of("j_joker_stencil", "Joker Stencil")
                        .whenHand().multiply(MULT, Val.stat(Value.Which.EMPTY_JOKER_SLOTS, 1.0, 1.0, null)).build(),
                Jokers.of("j_triboulet", "Triboulet")
                        .forEachScored(card().rankBetween(12, 13)).multiply(MULT, 2).build(),

                // --- ScoredFirst: retrigger the first scored card (Hanging Chad) ---
                Jokers.of("j_hanging_chad", "Hanging Chad")
                        .on(Trigger.REPETITION_PLAYED).when(card().isFirst())
                        .gives(Effect.Operation.ADD, Term.RETRIGGERS, Val.of(2)).build(),

                // --- ScoringContainsSuit: suit-coverage xMult jokers ---
                Jokers.of("j_flower_pot", "Flower Pot")
                        .whenHand(Cond.all(playedHand().hasSuit(Suit.DIAMONDS), playedHand().hasSuit(Suit.CLUBS),
                                playedHand().hasSuit(Suit.HEARTS), playedHand().hasSuit(Suit.SPADES)))
                        .multiply(MULT, 3).build(),
                Jokers.of("j_seeing_double", "Seeing Double")
                        .whenHand(Cond.all(playedHand().hasSuit(Suit.CLUBS), any(playedHand().hasSuit(Suit.DIAMONDS),
                                playedHand().hasSuit(Suit.HEARTS), playedHand().hasSuit(Suit.SPADES))))
                        .multiply(MULT, 2).build(),

                // --- global hand-evaluation modifiers (HandMod): change what hands form ---
                Jokers.of("j_four_fingers", "Four Fingers")
                        .handMod(HandMod.FOUR_FINGERS).build(),
                Jokers.of("j_shortcut", "Shortcut")
                        .handMod(HandMod.SHORTCUT).build(),
                Jokers.of("j_smeared_joker", "Smeared Joker")
                        .handMod(HandMod.SMEARED).build(),

                // --- retrigger jokers (existing algebra) ---
                Jokers.of("j_mime", "Mime")
                        .on(Trigger.REPETITION_HELD).retrigger().build(),
                Jokers.of("j_dusk", "Dusk")
                        .on(Trigger.REPETITION_PLAYED).when(runVar(Hand.PLAYS).atMost(1)).retrigger().build(),

                // --- Gold-card economy when scored ---
                Jokers.of("j_golden_ticket", "Golden Ticket")
                        .forEachScored(card().enhancement(Enhancement.GOLD)).add(DOLLARS, 4).build(),

                // --- batch 2: first-face / stat-threshold / held-suit conditions ---
                Jokers.of("j_photograph", "Photograph")
                        .forEachScored(card().firstFace()).multiply(MULT, 2).build(),
                Jokers.of("j_drivers_license", "Driver's License")
                        .whenHand(value(Val.stat(Value.Which.ENHANCED_CARD_COUNT, 0, 1, null)).atLeast(16))
                        .multiply(MULT, 3).build(),
                Jokers.of("j_blackboard", "Blackboard")
                        .whenHand(held().allSuits(Suit.SPADES, Suit.CLUBS)).multiply(MULT, 3).build(),

                // --- batch 3: global scoring modifiers ---
                Jokers.of("j_pareidolia", "Pareidolia")
                        .handMod(HandMod.PAREIDOLIA).build(),
                Jokers.of("j_splash", "Splash")
                        .handMod(HandMod.SPLASH).build(),

                // --- batch 4: lifecycle counter-scaling (state ships to client, JOKER_MAIN reads it) ---
                Jokers.of("j_green_joker", "Green Joker").counters("m")
                        .beforeScoring(always()).gain("m", 1)
                        .mutate(Trigger.PRE_DISCARD).when(always()).gain("m", -1)
                        .whenHand(state("m").atLeast(1)).add(MULT, Val.state("m")).build(),
                Jokers.of("j_fortune_teller", "Fortune Teller").counters("tarots")
                        .whenUsing("Tarot").gain("tarots", 1)
                        .whenHand(state("tarots").atLeast(1)).add(MULT, Val.state("tarots")).build(),

                // --- batch 5: stepwise / deck-size scaling values ---
                Jokers.of("j_bootstraps", "Bootstraps")
                        .whenHand().add(MULT, Val.runVarStep(Value.Var.MONEY, 0, 2, 5)).build(),
                Jokers.of("j_erosion", "Erosion")
                        .whenHand().add(MULT, Val.stat(Value.Which.CARDS_BELOW_FULL, 0, 4, null)).build(),

                // --- batch 6: passive standing modifiers (folded at blind start, like deck/voucher mods) ---
                modJoker("j_juggler", "Juggler",
                        Modify.add(Hand.SIZE, 1)),
                modJoker("j_drunkard", "Drunkard",
                        Modify.add(Hand.DISCARDS, 1)),
                modJoker("j_troubadour", "Troubadour",
                        Modify.add(Hand.PLAYS, -1), Modify.add(Hand.SIZE, 2)),
                modJoker("j_merry_andy", "Merry Andy",
                        Modify.add(Hand.DISCARDS, 3), Modify.add(Hand.SIZE, -1)),
                Jokers.of("j_burglar", "Burglar")
                        .mods(Modify.add(Hand.PLAYS, 3), Modify.min(Hand.DISCARDS, 0)).build(),
                Jokers.of("j_stuntman", "Stuntman")
                        .whenHand().add(CHIPS, 250)
                        .mods(Modify.add(Hand.SIZE, -2)).build(),

                // --- batch 7: held-card extreme value ---
                Jokers.of("j_raised_fist", "Raised Fist")
                        .whenHand().add(MULT, Val.lowestHeld(0, 2)).build(),

                // --- batch 8: end-of-round economy (END_OF_ROUND credits DOLLARS) ---
                Jokers.of("j_cloud_9", "Cloud 9")
                        .atEndOfRound().add(DOLLARS, Val.deckRankCount(9, 0, 1)).build(),
                Jokers.of("j_rocket", "Rocket").counters("bosses")
                        .mutate(Trigger.END_OF_ROUND).when(Cond.bossDefeated()).gain("bosses", 1)
                        .atEndOfRound().add(DOLLARS, Val.state("bosses", 1, 2)).build(),
                Jokers.of("j_delayed_gratification", "Delayed Gratification")
                        .atEndOfRound().when(not(runVar(Value.Var.DISCARDS_USED).atLeast(1)))
                        .add(DOLLARS, Val.runVar(Hand.DISCARDS, 0, 2)).build(),

                // --- batch 9: consumable creation ---
                Jokers.of("j_8_ball", "8 Ball")
                        .forEachScored(Cond.all(card().rankBetween(8, 8), Cond.chance(1, 4, "8ball")))
                        .create(CreateSpec.Kind.TAROT).build(),
                Jokers.of("j_cartomancer", "Cartomancer")
                        .on(Trigger.BLIND_SELECTED).create(CreateSpec.Kind.TAROT).build(),
                Jokers.of("j_vagabond", "Vagabond")
                        .whenHand(not(runVar(Value.Var.MONEY).atLeast(5)))
                        .create(CreateSpec.Kind.TAROT).build(),
                Jokers.of("j_superposition", "Superposition")
                        .whenHand(Cond.all(playedHand().contains(HandType.STRAIGHT),
                                value(Val.count(Value.Source.SCORING,
                                        card().rankBetween(14, 14), 0, 1)).atLeast(1)))
                        .create(CreateSpec.Kind.TAROT).build(),
                Jokers.of("j_seance", "Seance")
                        .whenHand(playedHand().is(HandType.STRAIGHT_FLUSH))
                        .create(CreateSpec.Kind.SPECTRAL).build(),

                // --- batch 11: joker / playing-card creation + card destruction ---
                Jokers.of("j_marble", "Marble Joker")
                        .on(Trigger.BLIND_SELECTED)
                        .create(new CreateSpec(CreateSpec.Kind.PLAYING_CARD, 1, null, Enhancement.STONE)).build(),
                Jokers.of("j_riff_raff", "Riff-Raff")
                        .on(Trigger.BLIND_SELECTED)
                        .create(new CreateSpec(CreateSpec.Kind.JOKER, 2, "Common", null)).build(),
                Jokers.of("j_sixth_sense", "Sixth Sense")
                        .forEachScored(Cond.all(playedHand().sizeExactly(1), card().rankBetween(6, 6)))
                        .effect(new Effect.Destroy(new Selector.Focus()), new Effect.Create(new CreateSpec(CreateSpec.Kind.SPECTRAL))).build(),

                // --- batch 12: hand level-up ---
                Jokers.of("j_space", "Space Joker")
                        .whenHand(Cond.chance(1, 4, "space")).levelUpHand(1).build(),
                // --- batch 13: destruction-counting xMult (CARD_DESTROYED) ---
                Jokers.of("j_glass_joker", "Glass Joker").counters("x")
                        .whenCardDestroyed(card().enhancement(Enhancement.GLASS)).gain("x", 0.75)
                        .whenHand().multiply(MULT, Val.state("x", 1.0, 1.0)).build(),
                Jokers.of("j_canio", "Canio").counters("x")
                        .whenCardDestroyed(card().isFace()).gain("x", 1)
                        .whenHand().multiply(MULT, Val.state("x", 1.0, 1.0)).build(),
                Jokers.of("j_yorick", "Yorick").counters("d")
                        .mutate(Trigger.PRE_DISCARD).when(always()).gainPerCard("d", 1, always())
                        .whenHand().multiply(MULT, Val.stateStep("d", 1.0, 1.0, 23)).build(),
                Jokers.of("j_hit_the_road", "Hit the Road").counters("x")
                        .mutate(Trigger.BLIND_SELECTED).when(always()).reset("x")
                        // +0.5 per Jack in the discarded set
                        .mutate(Trigger.PRE_DISCARD).when(always()).gainPerCard("x", 0.5, card().rankBetween(11, 11))
                        .whenHand().multiply(MULT, Val.state("x", 1.0, 1.0)).build(),

                // count discards this round (reset at blind select); mutations run before rules,
                // so on the first discard the counter is exactly 1 when the rule is checked.
                Jokers.of("j_burnt", "Burnt Joker").counters("discards")
                        .mutate(Trigger.BLIND_SELECTED).when(always()).reset("discards")
                        .mutate(Trigger.PRE_DISCARD).when(always()).gain("discards", 1)
                        .whenDiscarding(Cond.all(state("discards").atLeast(1), not(state("discards").atLeast(2))))
                        .levelUpHand(1).build(),

                // --- batch 16: card copy (DNA) + sealed card creation (Certificate) ---
                Jokers.of("j_dna", "DNA")
                        .forEachScored(Cond.all(playedHand().sizeExactly(1),
                                not(runVar(Value.Var.HANDS_PLAYED).atLeast(1))))
                        .copyScored().build(),
                Jokers.of("j_hologram", "Hologram").counters("x")
                        .mutate(Trigger.CARD_ADDED).when(always()).gain("x", 0.25)
                        .whenHand().multiply(MULT, Val.state("x", 1.0, 1.0)).build(),
                Jokers.of("j_certificate", "Certificate")
                        .on(Trigger.FIRST_HAND_DRAWN)
                        .create(new CreateSpec(CreateSpec.Kind.PLAYING_CARD, 1, null, null, null, true)).build(),

                // --- batch 18: decay jokers (run-long counters + clamped values) ---
                Jokers.of("j_ice_cream", "Ice Cream")
                        .whenHand().add(CHIPS, Val.clamp(Val.runVar(Value.Var.HANDS_PLAYED_TOTAL, 100, -5), 0, 1e9))
                        .on(Trigger.END_OF_ROUND)
                        .when(Cond.value(Val.runVar(Value.Var.HANDS_PLAYED_TOTAL, 100, -5)).atMost(0))
                        .effect(new Effect.Destroy(new Selector.Self())).build(),
                Jokers.of("j_popcorn", "Popcorn")
                        .whenHand().add(MULT, Val.clamp(Val.runVar(Value.Var.ROUNDS_PLAYED, 20, -4), 0, 1e9))
                        .on(Trigger.END_OF_ROUND)
                        .when(Cond.value(Val.runVar(Value.Var.ROUNDS_PLAYED, 20, -4)).atMost(0))
                        .effect(new Effect.Destroy(new Selector.Self())).build(),
                // --- batch 42: Matador (boss-ability interaction) ---
                Jokers.of("j_matador", "Matador")
                        .whenHand(Cond.bossAbilityActive()).add(DOLLARS, 8).build(),

                // --- batch 41: tags (Diet Cola) ---
                Jokers.of("j_diet_cola", "Diet Cola")
                        .on(Trigger.SELL_SELF).effect(new Effect.CreateTag("tag_double")).build(),

                // --- batch 40: booster packs (Hallucination, Red Card) ---
                Jokers.of("j_hallucination", "Hallucination")
                        .on(Trigger.OPEN_BOOSTER).when(Cond.chance(1, 2, "hallucination"))
                        .create(CreateSpec.Kind.TAROT).build(),
                Jokers.of("j_red_card", "Red Card").counters("mult")
                        .mutate(Trigger.SKIP_BOOSTER).when(always()).gain("mult", 3)
                        .whenHand(state("mult").atLeast(1)).add(MULT, Val.state("mult")).build(),

                // --- batch 39: Showman (allow duplicate shop offerings; disables the skip-if-owned rule) ---
                Jokers.of("j_showman", "Showman")
                        .mods(Modify.max(Value.Var.ALLOW_SHOP_DUPLICATES, 1)).build(),

                // --- batch 38: blind-skipping (Throwback) ---
                Jokers.of("j_throwback", "Throwback")
                        .whenHand().multiply(MULT, Val.runVar(Value.Var.BLINDS_SKIPPED, 1, 0.25)).build(),

                // --- batch 37: "since acquired" jokers via acquire-stamp (Turtle Bean, Seltzer) ---
                // Turtle Bean: +4 hand size (BMP 0.4.2; vanilla +5), decaying by 1 each round — a DYNAMIC
                // Modify now (the amount is a Value: clamp(4 − roundsPlayed, 0, ∞)), not a RunMod.
                Jokers.of("j_turtle_bean", "Turtle Bean")
                        .mods(Modify.add(Hand.SIZE, turtleBeanDecay(4))).build(),
                Jokers.of("j_seltzer", "Seltzer")
                        .on(Trigger.REPETITION_PLAYED).when(Cond.handsSinceAcquired(10)).retrigger().build(),

                // --- batch 36: Obelisk (consecutive non-most-played streak; shipped, preview-accurate) ---
                Jokers.of("j_obelisk", "Obelisk")
                        .whenHand().multiply(MULT, Val.runVar(Value.Var.OBELISK_STREAK, 1, 0.2)).build(),

                // --- batch 35: Loyalty Card (every-6-hands xMult; preview-accurate via shipped counter) ---
                // hands-played is incremented AFTER scoring, so this hand is play #(total+1);
                // x4 on the 6th, 12th, ... -> total % 6 == 5. Shipped counter -> previews exactly.
                Jokers.of("j_loyalty_card", "Loyalty Card")
                        .whenHand(Cond.runVarModulo(Value.Var.HANDS_PLAYED_TOTAL, 6, 5)).multiply(MULT, 4).build(),

                // --- batch 34: Trading Card — first single-card discard of the round: destroy it, +$3.
                //     Pure data: PRE_DISCARD, condition (first discard AND one card discarded), then a
                //     compound effect (+$3 and destroy the discarded set). No behaviorInCode. ---
                Jokers.of("j_trading", "Trading Card")
                        .whenDiscarding(Cond.all(runVar(Value.Var.DISCARDS_USED).exactly(0),
                                value(Val.count(Value.Source.EVENT, always(), 0, 1)).exactly(1)))
                        .effect(Effect.dollars(Val.of(3)), new Effect.Destroy(new Selector.Discarded()))
                        .build(),

                // --- batch 33: shop-exit / sell-self lifecycle (Perkeo, Invisible, Luchador) ---
                Jokers.of("j_perkeo", "Perkeo")
                        .on(Trigger.SHOP_EXIT).effect(new Effect.Copy(new com.balatro.grammar.Selector.RandomConsumable(), 1)).build(),
                Jokers.of("j_invisible", "Invisible Joker").counters("rounds")
                        .mutate(Trigger.END_OF_ROUND).when(always()).gain("rounds", 1)
                        // The "owned ≥ 2 rounds" gate is a Condition, not baked into the verb; the effect is a plain Copy.
                        .on(Trigger.SELL_SELF).when(Cond.state("rounds").atLeast(2))
                        .effect(new Effect.Copy(new Selector.RandomJoker(), 1)).build(),
                Jokers.of("j_luchador", "Luchador")
                        .on(Trigger.SELL_SELF).effect(new Effect.DisableBoss()).build(),

                // --- batch 32: joker-destroyers (Ceremonial Dagger, Madness) ---
                Jokers.of("j_ceremonial", "Ceremonial Dagger").counters("mult")
                        .whenHand(state("mult").atLeast(1)).add(MULT, Val.state("mult"))
                        .on(Trigger.BLIND_SELECTED).effect(new Effect.Destroy(new Selector.OtherJoker(Selector.OtherJoker.Scope.RIGHT_NEIGHBOR, true))).build(),
                // x0.5 Mult is a state-write rule; eating a random joker is a BLIND_SELECTED destroy rule.
                Jokers.of("j_madness", "Madness").counters("xm")
                        .mutate(Trigger.BLIND_SELECTED).when(not(Cond.bossBlind())).gain("xm", 0.5)
                        .whenHand(state("xm").atLeast(0.5)).multiply(MULT, Val.state("xm", 1.0, 1.0))
                        .on(Trigger.BLIND_SELECTED).effect(new Effect.Destroy(new Selector.OtherJoker(Selector.OtherJoker.Scope.RANDOM_OTHER, false))).build(),

                // --- batch 31: Satellite (unique-planet economy) ---
                Jokers.of("j_satellite", "Satellite")
                        .atEndOfRound().add(DOLLARS, Val.runVar(Value.Var.UNIQUE_PLANETS, 0, 1)).build(),

                // --- batch 30: Oops! All 6s (probability doubler) + Reserved Parking ---
                Jokers.of("j_oops", "Oops! All 6s")
                        .mods(Modify.multiply(Value.Var.PROBABILITY_MULTIPLIER, 2)).build(),
                Jokers.of("j_reserved_parking", "Reserved Parking")
                        .forEachHeld(Cond.all(card().isFace(), Cond.chance(1, 2, "reserved_parking"))).add(DOLLARS, 1).build(),

                // --- batch 29: boss-ability disable (Chicot) — a passive capability, expressed as data ---
                // Chicot: while owned, every Boss Blind's ability is disabled — a dynamic boolean policy
                // (Modify on BOSS_ABILITY_DISABLED, folded from owned jokers), not a RunMod capability.
                Jokers.of("j_chicot", "Chicot")
                        .mods(Modify.max(Value.Var.BOSS_ABILITY_DISABLED, 1)).build(),

                // --- batch 28: sell-value bonus (Egg, Gift Card) ---
                Jokers.of("j_egg", "Egg").counters("sellBonus")
                        .mutate(Trigger.END_OF_ROUND).when(always()).gain("sellBonus", 3).build(),
                Jokers.of("j_gift_card", "Gift Card").counters("sellBonus")
                        .mutate(Trigger.END_OF_ROUND).when(always()).gainEveryJoker("sellBonus", 1).build(),

                // --- batch 27: shop/economy hooks (Credit Card, Chaos, Astronomer) ---
                Jokers.of("j_credit_card", "Credit Card")
                        .mods(Modify.min(Value.Var.MIN_MONEY, -20)).build(),
                Jokers.of("j_chaos", "Chaos the Clown")
                        .mods(Modify.add(Value.Var.FREE_REROLLS, 1)).build(),
                Jokers.of("j_astronomer", "Astronomer")
                        .mods(Modify.max(Value.Var.PLANETS_FREE, 1)).build(),

                // --- batch 26: Run-level hooks (To the Moon interest) ---
                // Mr Bones: survive a failed blind (and self-destruct) if you reached 25% of the requirement.
                // A data rule on the Blind lifecycle — BLIND_LOST gated on BLIND_PROGRESS — not a RunMod.
                Jokers.of("j_mr_bones", "Mr. Bones")
                        .on(Trigger.BLIND_LOST).when(Cond.runVar(Value.Var.BLIND_PROGRESS).atLeast(0.25))
                        .effect(new Effect.SurviveBlind()).build(),
                Jokers.of("j_to_the_moon", "To the Moon")
                        .mods(Modify.max(Value.Var.UNCAPPED_INTEREST, 1)).build(),

                // --- batch 25: Mail-In Rebate (event-count money) ---
                Jokers.of("j_mail_in_rebate", "Mail-In Rebate")
                        .whenDiscarding(always()).add(DOLLARS, Val.count(Value.Source.EVENT,
                                card().rankIsTarget("rebateRankId"), 0, 5)).build(),

                // --- batch 24: more dynamic targets (Castle chips, To Do List money) ---
                Jokers.of("j_castle", "Castle").counters("chips")
                        .mutate(Trigger.BLIND_SELECTED).when(always()).reset("chips")
                        .mutate(Trigger.PRE_DISCARD).when(always()).gainPerCard("chips", 3, card().suitIsTarget("castleSuit"))
                        .whenHand(state("chips").atLeast(1)).add(CHIPS, Val.state("chips")).build(),
                Jokers.of("j_todo_list", "To Do List")
                        .whenHand(playedHand().isTarget("todoHand")).add(DOLLARS, 4).build(),

                // --- batch 23: per-round dynamic targets (The Idol, Ancient Joker) ---
                Jokers.of("j_idol", "The Idol")
                        .forEachScored(Cond.all(card().rankIsTarget("idolRankId"),
                                card().suitIsTarget("idolSuit"))).multiply(MULT, 2).build(),
                Jokers.of("j_ancient_joker", "Ancient Joker")
                        .forEachScored(card().suitIsTarget("ancientSuit")).multiply(MULT, 1.5).build(),

                // --- batch 22: joker-on-joker reads (Baseball Card, Swashbuckler) ---
                Jokers.of("j_baseball_card", "Baseball Card")
                        .on(Trigger.ON_OTHER_JOKER).when(Cond.otherJokerRarity("Uncommon")).multiply(MULT, 1.5).build(),
                Jokers.of("j_swashbuckler", "Swashbuckler")
                        .whenHand().add(MULT, Val.otherJokersSellSum(0, 1)).build(),

                // --- batch 21: per-hand-type play tracking (Supernova, Card Sharp) ---
                Jokers.of("j_supernova", "Supernova")
                        .whenHand().add(MULT, Val.handTypePlays(1, 1)).build(),
                Jokers.of("j_card_sharp", "Card Sharp")
                        .whenHand(playedHand().repeatedThisRound()).multiply(MULT, 3).build(),

                // --- batch 20: Lucky Cat (counts Lucky triggers) ---
                Jokers.of("j_lucky_cat", "Lucky Cat")
                        .whenHand().multiply(MULT, Val.runVar(Value.Var.LUCKY_TRIGGERS, 1, 0.25)).build(),

                // --- batch 19: sell action (Campfire) ---
                Jokers.of("j_campfire", "Campfire").counters("x")
                        .mutate(Trigger.SELL_CARD).when(always()).gain("x", 0.25)
                        .mutate(Trigger.END_OF_ROUND).when(Cond.bossDefeated()).reset("x")
                        .whenHand().multiply(MULT, Val.state("x", 1.0, 1.0)).build(),

                Jokers.of("j_ramen", "Ramen")
                        .whenHand().multiply(MULT, Val.clamp(
                                Val.runVar(Value.Var.CARDS_DISCARDED_TOTAL, 2, -0.01), 1, 1e9))
                        .on(Trigger.PRE_DISCARD)
                        .when(Cond.value(Val.runVar(Value.Var.CARDS_DISCARDED_TOTAL, 2, -0.01)).atMost(1))
                        .effect(new Effect.Destroy(new Selector.Self())).build());
    }


    /** A joker whose only effect is standing variable {@link Modify}s (Juggler, Drunkard, Troubadour…). */
    private static JokerDef modJoker(String key, String name, Modify... mods) {
        return Jokers.of(key, name).mods(mods).build();
    }
}
