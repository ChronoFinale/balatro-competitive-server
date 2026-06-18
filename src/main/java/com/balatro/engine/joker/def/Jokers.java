package com.balatro.engine.joker.def;

import com.balatro.engine.card.CardMod;
import com.balatro.engine.hand.HandMod;
import com.balatro.engine.joker.Trigger;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link JokerDef}s — author the card language as readable sentences, with full IDE
 * autocomplete. It produces exactly the data a JSON ruleset would carry (the records stay the source of
 * truth); this is just the in-code authoring form.
 *
 * <pre>{@code
 * Jokers.common("j_sly_joker", "Sly Joker").cost(3).atlas(0, 14)
 *       .desc("+50 Chips if the played hand contains a Pair")
 *       .on(Trigger.JOKER_MAIN).when(Cond.handPlayed().containsPair()).chips(50)
 *       .build();
 * }</pre>
 */
public final class Jokers {

    private final String key;
    private final String name;
    private final String rarity;
    private int cost;
    private boolean costSet;
    private int atlasX;
    private int atlasY;
    private String desc = "";
    private boolean blueprintCompatible = true;
    private CopySpec copy;
    private final List<Rule> rules = new ArrayList<>();
    private final List<Mutation> mutations = new ArrayList<>();
    private final List<HandMod> handMods = new ArrayList<>();
    private final List<Modify> varMods = new ArrayList<>();
    private RunMod runMod = RunMod.NONE;
    private boolean behaviorInCode;
    private final java.util.Map<String, Object> props = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();

    private Jokers(String key, String name, String rarity) {
        this.key = key;
        this.name = name;
        this.rarity = rarity;
    }

    public static Jokers common(String key, String name) { return new Jokers(key, name, "Common"); }

    public static Jokers uncommon(String key, String name) { return new Jokers(key, name, "Uncommon"); }

    public static Jokers rare(String key, String name) { return new Jokers(key, name, "Rare"); }

    public static Jokers legendary(String key, String name) { return new Jokers(key, name, "Legendary"); }

    public Jokers cost(int c) { this.cost = c; this.costSet = true; return this; }

    public Jokers atlas(int x, int y) { this.atlasX = x; this.atlasY = y; return this; }

    public Jokers desc(String d) { this.desc = d; return this; }

    /** Mark this joker un-copyable by Blueprint/Brainstorm (the copiers themselves). */
    public Jokers notCopyable() { this.blueprintCompatible = false; return this; }

    /** This joker copies another's effect (Blueprint/Brainstorm) — the higher-order {@link CopySpec}. */
    public Jokers copies(CopySpec.Selector selector) { this.copy = new CopySpec(selector); return this; }

    /** Declare a named constant property (a number, or a domain enum like a Suit) — referenced via Val.prop. */
    public Jokers prop(String name, Object value) { this.props.put(name, value); return this; }

    /** Declare a persistent state variable with its initial value (its typed memory). */
    public Jokers state(String name, Object initial) { this.state.put(name, initial); return this; }

    /** Begin a scoring rule fired on {@code trigger}: chain {@code .when(cond)} then a terminal effect. */
    public RuleBuilder on(Trigger trigger) { return new RuleBuilder(trigger); }

    // --- intent-revealing rule verbs: the QUANTIFIER is explicit, so you never have to know that a raw
    //     trigger secretly means "per card" vs "once". They're thin sugar over on(trigger).when(...). ---

    /** PER scored card: "for each scored card matching {@code each}, do …" (Greedy, Even Steven). */
    public RuleBuilder forEachScored(Condition each) { return new RuleBuilder(Trigger.ON_SCORED).when(each); }

    /** PER held-in-hand card matching {@code each} (Steel-reactive jokers). */
    public RuleBuilder forEachHeld(Condition each) { return new RuleBuilder(Trigger.ON_HELD).when(each); }

    /** ONCE per hand, when the played hand satisfies {@code cond} (Sly, Half). */
    public RuleBuilder whenHand(Condition cond) { return new RuleBuilder(Trigger.JOKER_MAIN).when(cond); }

    /** ONCE per hand, unconditionally — the joker's main effect (plain Joker, Constellation's xMult). */
    public RuleBuilder whenHand() { return new RuleBuilder(Trigger.JOKER_MAIN); }

    /** ONCE at end of round — economy (Golden Joker). */
    public RuleBuilder atEndOfRound() { return new RuleBuilder(Trigger.END_OF_ROUND); }

    /** ONCE when discarding, if {@code cond} holds over the discarded set (Faceless). */
    public RuleBuilder whenDiscarding(Condition cond) { return new RuleBuilder(Trigger.PRE_DISCARD).when(cond); }

    /** Retrigger each scored card matching {@code each} (Hack) — a complete rule. */
    public Jokers retriggerEachScored(Condition each) {
        return new RuleBuilder(Trigger.REPETITION_PLAYED).when(each).retrigger();
    }

    // --- event/state moments: react to something happening elsewhere by changing persistent memory.
    //     They return a MutationBuilder (gain/set/reset), since events usually mutate state. ---

    /** When a consumable matching {@code cond} is used (Constellation gains a Planet). */
    public MutationBuilder whenUsing(Condition cond) { return new MutationBuilder(Trigger.USE_CONSUMABLE).when(cond); }

    /** When a consumable of {@code type} ("Planet"/"Tarot"/"Spectral") is used — the common shorthand. */
    public MutationBuilder whenUsing(String type) { return whenUsing(new Condition.ConsumableType(type)); }

    /** When a card matching {@code cond} is destroyed (Canio: a face card → +xMult). */
    public MutationBuilder whenCardDestroyed(Condition cond) { return new MutationBuilder(Trigger.CARD_DESTROYED).when(cond); }

    /** Before scoring, if {@code cond} holds (Ride the Bus's streak check). */
    public MutationBuilder beforeScoring(Condition cond) { return new MutationBuilder(Trigger.BEFORE).when(cond); }

    /** Escape hatch: a state mutation on a raw {@code trigger} (for moments without a named verb). */
    public MutationBuilder mutate(Trigger trigger) { return new MutationBuilder(trigger); }

    /** Attach passive hand modifiers (Four Fingers, Shortcut, Splash, Pareidolia, Smeared). */
    public Jokers handMod(HandMod... mods) { java.util.Collections.addAll(handMods, mods); return this; }

    /** Standing variable modifiers while owned — {@code mods(add(HAND_SIZE,1))} (Juggler), {@code
     *  add(FREE_REROLLS,1)} (Chaos). The SAME {@link Modify} vocabulary decks/vouchers use, folded by Run. */
    public Jokers mods(Modify... mods) { java.util.Collections.addAll(varMods, mods); return this; }

    /** Attach the run-capability bag (boss disabler, probability doubler, hand-size decay, sell hooks…). */
    public Jokers runMod(RunMod mod) { this.runMod = mod; return this; }

    /** Escape hatch: append a fully-built {@link Rule} (a trigger/effect combination without a fluent verb). */
    public Jokers rule(Rule r) { rules.add(r); return this; }

    /** Escape hatch: append a fully-built {@link Mutation}. */
    public Jokers mutation(Mutation m) { mutations.add(m); return this; }

    /** This joker carries no def behavior — its effect lives in engine code (ShopConfig/Match/IntentHandler).
     *  A deliberate marker (Showman/Pizza/Speedrun); lets {@code build()} accept an otherwise-empty def. */
    public Jokers behaviorInCode() { this.behaviorInCode = true; return this; }

    public JokerDef build() {
        // Fail loud and COMPLETE: list every missing required field by name, so you never have to guess
        // what a joker needs (the error is the documentation). Identity (key/name/rarity) is enforced by
        // the factory; this covers the rest.
        List<String> missing = new ArrayList<>();
        if (key == null || key.isBlank()) missing.add("key");
        if (name == null || name.isBlank()) missing.add("name");
        if (desc == null || desc.isBlank()) missing.add("description (.desc)");
        if (!costSet) missing.add("cost (.cost)");
        if (rules.isEmpty() && mutations.isEmpty() && copy == null && handMods.isEmpty()
                && varMods.isEmpty() && runMod.isNone() && !behaviorInCode) {
            missing.add("behavior (.on / .mutate / .copies / .handMod / .mods / .runMod / .behaviorInCode)");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Joker '" + key + "' is missing required: " + String.join(", ", missing));
        }
        return new JokerDef(key, name, desc, rarity, cost, atlasX, atlasY, null, null,
                blueprintCompatible, List.copyOf(mutations), List.copyOf(rules),
                List.copyOf(handMods), List.copyOf(varMods), runMod, copy,
                java.util.Map.copyOf(props), java.util.Map.copyOf(state));
    }

    /** A scoring rule under construction; a terminal effect commits it and returns the joker builder. */
    public final class RuleBuilder {
        private final Trigger trigger;
        private Condition condition = new Condition.Always();

        RuleBuilder(Trigger trigger) { this.trigger = trigger; }

        public RuleBuilder when(Condition c) { this.condition = c; return this; }

        private Jokers commit(EffectTemplate.Op op, Value v) {
            rules.add(new Rule(trigger, condition, new EffectTemplate(op, v)));
            return Jokers.this;
        }

        // The scoring algebra: add / times / lose to a Target (Mult, Chips, Dollars). The value can be a
        // literal, a declared binding (Val.prop), or a scaling expression (Val.perState). One uniform way
        // to express +Mult, x Mult, +Chips, +$, -$ — the primitives nearly every joker is built from.

        /** Add {@code v} to {@code t} (+Mult, +Chips, +$). */
        public Jokers add(Target t, double v) { return commit(addOp(t), new Value.Const(v)); }

        public Jokers add(Target t, Value v) { return commit(addOp(t), v); }

        /** Multiply {@code t} by {@code v} (x Mult). */
        public Jokers multiply(Target t, double v) { return commit(multiplyOp(t), new Value.Const(v)); }

        public Jokers multiply(Target t, Value v) { return commit(multiplyOp(t), v); }

        /** Subtract {@code v} from {@code t} (e.g. -$) — money goes both ways. */
        public Jokers subtract(Target t, double v) { return commit(addOp(t), new Value.Const(-v)); }

        /** Retrigger the matching card once (reads "for each … retrigger"). */
        public Jokers retrigger() { return commit(EffectTemplate.Op.REPETITIONS, new Value.Const(1)); }

        /** Effect with an explicit {@link Value} (per-card counts, run vars, ...). */
        public Jokers gives(EffectTemplate.Op op, Value v) { return commit(op, v); }

        // --- non-numeric effect terminals: read as verbs instead of .effect(EffectTemplate.x(...)) ---

        /** Create cards/consumables/jokers (8 Ball, Cartomancer, Riff-Raff, ...). */
        public Jokers create(CreateSpec spec) { return effect(EffectTemplate.create(spec)); }

        /** Create one of {@code kind} (the common single-card case). */
        public Jokers create(CreateSpec.Kind kind) { return effect(EffectTemplate.create(new CreateSpec(kind))); }

        /** Permanently mutate each matching card — enhance/convert/add-chips (Hiker, Midas Mask, Vampire). */
        public Jokers mutateCard(CardMod mod) { return effect(EffectTemplate.mutate(mod)); }

        /** Level up the played poker hand by {@code levels} (Space, Burnt). */
        public Jokers levelUpHand(int levels) { return effect(EffectTemplate.levelUpHand(levels)); }

        /** Add a permanent copy of the scored card to the deck (DNA). */
        public Jokers copyScored() { return effect(EffectTemplate.copyScored()); }

        private static EffectTemplate.Op addOp(Target t) {
            return switch (t) {
                case MULT -> EffectTemplate.Op.MULT;
                case CHIPS -> EffectTemplate.Op.CHIPS;
                case DOLLARS -> EffectTemplate.Op.DOLLARS;
            };
        }

        private static EffectTemplate.Op multiplyOp(Target t) {
            if (t != Target.MULT) {
                throw new IllegalArgumentException("multiply only supports MULT (x Mult); got " + t);
            }
            return EffectTemplate.Op.XMULT;
        }

        /** Fully-specified effect (escape hatch for ops without a fluent terminal yet). */
        public Jokers effect(EffectTemplate e) {
            rules.add(new Rule(trigger, condition, e));
            return Jokers.this;
        }
    }

    /** A state mutation under construction; {@code .add(...)} commits it. */
    public final class MutationBuilder {
        private final Trigger trigger;
        private Condition condition = new Condition.Always();

        MutationBuilder(Trigger trigger) { this.trigger = trigger; }

        public MutationBuilder when(Condition c) { this.condition = c; return this; }

        /** Add {@code by} to a state counter (Constellation: gain a planet; Canio: +1 xMult). */
        public Jokers gain(String var, double by) {
            mutations.add(new Mutation(trigger, condition, var, Mutation.Op.ADD, by));
            return Jokers.this;
        }

        /** Add {@code by} per event-card matching {@code perCard} (Hit the Road: +0.5 per Jack discarded). */
        public Jokers gainPerCard(String var, double by, Condition perCard) {
            mutations.add(new Mutation(trigger, condition, var, Mutation.Op.ADD, by, perCard));
            return Jokers.this;
        }

        /** Add {@code by} to this var on EVERY owned joker, not just self (Gift Card). */
        public Jokers gainEveryJoker(String var, double by) {
            mutations.add(new Mutation(trigger, condition, var, Mutation.Op.ADD, by, Mutation.Scope.ALL_JOKERS));
            return Jokers.this;
        }

        /** Set a state counter to {@code value}. */
        public Jokers set(String var, double value) {
            mutations.add(new Mutation(trigger, condition, var, Mutation.Op.SET, value));
            return Jokers.this;
        }

        /** Reset the counter to 0 (Ride the Bus's streak breaking on a face card). */
        public Jokers reset(String var) {
            mutations.add(new Mutation(trigger, condition, var, Mutation.Op.RESET, 0));
            return Jokers.this;
        }
    }
}
