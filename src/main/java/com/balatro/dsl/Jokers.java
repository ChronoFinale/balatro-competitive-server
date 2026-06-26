package com.balatro.dsl;

import com.balatro.engine.joker.def.*;
import com.balatro.grammar.*;


import com.balatro.engine.card.CardMod;
import com.balatro.engine.hand.HandMod;
import com.balatro.grammar.Trigger;
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
    private boolean atlasSet;
    private String desc = "";
    private boolean descSet;
    private boolean blueprintCompatible = true;
    private CopySpec copy;
    private final List<Rule> rules = new ArrayList<>();
    private final List<HandMod> handMods = new ArrayList<>();
    private final List<Modify> varMods = new ArrayList<>();
    private final java.util.Set<String> declaredCounters = new java.util.LinkedHashSet<>();
    private final java.util.Map<String, Object> props = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();

    private Jokers(String key, String name, String rarity) {
        this.key = key;
        this.name = name;
        this.rarity = rarity;
    }

    /** Author a built-in joker by key+name — rarity, cost, and sprite location are read from the ground-truth
     *  metadata table (the def carries only its effect). The rarity-named factories below are kept for custom/
     *  off-table jokers that set their own metadata. */
    public static Jokers of(String key, String name) { return new Jokers(key, name, null); }

    public static Jokers common(String key, String name) { return new Jokers(key, name, "Common"); }

    public static Jokers uncommon(String key, String name) { return new Jokers(key, name, "Uncommon"); }

    public static Jokers rare(String key, String name) { return new Jokers(key, name, "Rare"); }

    public static Jokers legendary(String key, String name) { return new Jokers(key, name, "Legendary"); }

    public Jokers cost(int c) { this.cost = c; this.costSet = true; return this; }

    /** Override the sprite location (rarely needed — by default it's read from the ground-truth metadata
     *  table by key, so effect defs don't hardcode sprite coords). */
    public Jokers atlas(int x, int y) { this.atlasX = x; this.atlasY = y; this.atlasSet = true; return this; }

    /** Override the description (rarely needed — by default it's read from the localization table by key, so
     *  display text isn't hardcoded in the effect def). */
    public Jokers desc(String d) { this.desc = d; this.descSet = true; return this; }

    /** Mark this joker un-copyable by Blueprint/Brainstorm (the copiers themselves). */
    public Jokers notCopyable() { this.blueprintCompatible = false; return this; }

    /** This joker copies another's effect (Blueprint/Brainstorm) — the higher-order {@link CopySpec}. */
    public Jokers copies(CopySpec.Selector selector) { this.copy = new CopySpec(selector); return this; }

    /** Declare a named constant property (a number, or a domain enum like a Suit) — referenced via Val.prop. */
    public Jokers prop(String name, Object value) { this.props.put(name, value); return this; }

    /** Declare a persistent state variable with its initial value (its typed memory). Also registers it as a
     *  counter — a declared state IS a known counter, so you never need both {@link #state} and {@link #counters}. */
    public Jokers state(String name, Object initial) {
        this.state.put(name, initial);
        this.declaredCounters.add(name);
        return this;
    }

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
    public MutationBuilder whenUsing(com.balatro.grammar.ConsumableKind type) { return whenUsing(new Condition.ConsumableType(type)); }

    /** When a card matching {@code cond} is destroyed (Canio: a face card → +xMult). */
    public MutationBuilder whenCardDestroyed(Condition cond) { return new MutationBuilder(Trigger.CARD_DESTROYED).when(cond); }

    /** Before scoring, if {@code cond} holds (Ride the Bus's streak check). */
    public MutationBuilder beforeScoring(Condition cond) { return new MutationBuilder(Trigger.BEFORE).when(cond); }

    /** Escape hatch: a state mutation on a raw {@code trigger} (for moments without a named verb). */
    public MutationBuilder mutate(Trigger trigger) { return new MutationBuilder(trigger); }

    /** Attach passive hand modifiers (Four Fingers, Shortcut, Splash, Pareidolia, Smeared). */
    public Jokers handMod(HandMod... mods) { java.util.Collections.addAll(handMods, mods); return this; }

    /** Standing variable modifiers while owned — {@code mods(add(Hand.SIZE,1))} (Juggler), {@code
     *  add(FREE_REROLLS,1)} (Chaos). The SAME {@link Modify} vocabulary decks/vouchers use, folded by Run. */
    public Jokers mods(Modify... mods) { java.util.Collections.addAll(varMods, mods); return this; }

    /** Escape hatch: append a fully-built {@link Rule} (a trigger/effect combination without a fluent verb). */
    public Jokers rule(Rule r) { rules.add(r); return this; }

    /**
     * Register this joker's scaling COUNTERS — the named state it accumulates into and reads back. Every
     * state key the rules touch ({@code gain}/{@code set}/{@code reset} writes and {@code Val.state} reads,
     * plus engine-written keys this joker reads) must be declared here, or {@link #build()} throws. This is
     * the Counter primitive's safety: a typo can't silently create an orphan key — an unregistered counter
     * fails the build, not a runtime no-op (the old magic-string trap).
     */
    public Jokers counters(String... names) {
        for (String n : names) {
            declaredCounters.add(n);
            state.putIfAbsent(n, 0); // a counter IS declared state (init 0) — serialized into the def, so the
                                     // def is the single source of truth for what state the joker owns.
        }
        return this;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper COUNTER_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** Collect every state key the rules read or write (mutateState / state / stateStep), via the def tree. */
    private static void collectCounters(com.fasterxml.jackson.databind.JsonNode node, java.util.Set<String> out) {
        if (node.isObject()) {
            String t = node.path("type").asText("");
            if ((t.equals("mutateState") || t.equals("state") || t.equals("stateStep")) && node.has("var")) {
                out.add(node.get("var").asText());
            }
            node.forEach(c -> collectCounters(c, out));
        } else if (node.isArray()) {
            node.forEach(c -> collectCounters(c, out));
        }
    }

    public JokerDef build() {
        // Fail loud and COMPLETE: list every missing required field by name, so you never have to guess
        // what a joker needs (the error is the documentation). Identity (key/name/rarity) is enforced by
        // the factory; this covers the rest.
        List<String> missing = new ArrayList<>();
        if (key == null || key.isBlank()) missing.add("key");
        if (name == null || name.isBlank()) missing.add("name");
        if (!descSet && !JokerLoc.has(key)) missing.add("description (.desc, or be in the localization table)");
        if (!costSet && !JokerMeta.has(key)) missing.add("cost (.cost, or be in the metadata table)");
        if (rules.isEmpty() && copy == null && handMods.isEmpty() && varMods.isEmpty()) {
            // Every joker is data — there is no empty-def escape hatch. Express the effect as a rule/mod.
            missing.add("behavior (.on / .mutate / .copies / .handMod / .mods)");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Joker '" + key + "' is missing required: " + String.join(", ", missing));
        }
        // Context-capability check: a rule's condition can only read context its trigger provides, else it
        // would silently never fire (the null-safe evaluator hides it). Fail loudly at authoring instead.
        for (var r : rules) RuleValidator.validate(key, r.when(), r.condition());
        // Counter registration: every state key the rules touch must be declared via .counters(...).
        java.util.Set<String> usedCounters = new java.util.TreeSet<>();
        collectCounters(COUNTER_MAPPER.valueToTree(rules), usedCounters);
        java.util.Set<String> unregistered = new java.util.TreeSet<>(usedCounters);
        unregistered.removeAll(declaredCounters);
        if (!unregistered.isEmpty()) {
            throw new IllegalStateException("Joker '" + key + "' uses unregistered counter(s) " + unregistered
                    + " — declare them with .counters(" + String.join(", ",
                    unregistered.stream().map(c -> '"' + c + '"').toList()) + ")");
        }
        if (!atlasSet) { // sprite location is data, not code: read it from the ground-truth metadata table
            int[] a = JokerMeta.atlas(key);
            atlasX = a[0];
            atlasY = a[1];
        }
        // rarity and cost are metadata too: a def with of(...) and no .cost() sources them from the table.
        String resolvedRarity = (rarity != null) ? rarity : JokerMeta.rarity(key);
        int resolvedCost = costSet ? cost : JokerMeta.cost(key);
        String resolvedDesc = descSet ? desc : JokerLoc.description(key); // text is localization data, not code
        return new JokerDef(key, name, resolvedDesc, resolvedRarity, resolvedCost, atlasX, atlasY, null, null,
                blueprintCompatible, List.copyOf(rules),
                List.copyOf(handMods), List.copyOf(varMods), copy,
                java.util.Map.copyOf(props), java.util.Map.copyOf(state));
    }

    /** A scoring rule under construction; a terminal effect commits it and returns the joker builder. */
    public final class RuleBuilder {
        private final Trigger trigger;
        private Condition condition = new Condition.Always();

        RuleBuilder(Trigger trigger) { this.trigger = trigger; }

        public RuleBuilder when(Condition c) { this.condition = c; return this; }

        private Jokers commit(Effect.Operation op, Effect.Term subject, Value v) {
            return effect(new Effect.Score(op, subject, v));
        }

        // The scoring algebra: add / times / lose to a {@link Effect.Term} (Mult, Chips, Dollars, …). The
        // value can be a literal, a declared binding (Val.prop), or a scaling expression (Val.perState). One
        // uniform way to express +Mult, x Mult, +Chips, +$, -$ — the primitives nearly every joker is built
        // from. The operation (add/multiply) is the verb; the Term is what it lands in — independent axes.

        /** Add {@code v} to term {@code t} (+Mult, +Chips, +$). */
        public Jokers add(Effect.Term t, double v) { return commit(Effect.Operation.ADD, t, new Value.Const(v)); }

        public Jokers add(Effect.Term t, Value v) { return commit(Effect.Operation.ADD, t, v); }

        /** Multiply term {@code t} by {@code v} (x Mult). */
        public Jokers multiply(Effect.Term t, double v) { return multiply(t, new Value.Const(v)); }

        public Jokers multiply(Effect.Term t, Value v) {
            if (t != Effect.Term.MULT) throw new IllegalArgumentException("multiply only supports MULT (x Mult); got " + t);
            return commit(Effect.Operation.MULTIPLY, Effect.Term.MULT, v);
        }

        /** Subtract {@code v} from term {@code t} (e.g. -$) — money goes both ways. */
        public Jokers subtract(Effect.Term t, double v) { return commit(Effect.Operation.ADD, t, new Value.Const(-v)); }

        /** Retrigger the matching card once (reads "for each … retrigger"). */
        public Jokers retrigger() { return commit(Effect.Operation.ADD, Effect.Term.RETRIGGERS, new Value.Const(1)); }

        /** Effect with an explicit operation + subject and a {@link Value} (per-card counts, run vars, ...). */
        public Jokers gives(Effect.Operation op, Effect.Term subject, Value v) { return commit(op, subject, v); }

        // --- non-numeric effect terminals: read as verbs ---

        /** Create cards/consumables/jokers (8 Ball, Cartomancer, Riff-Raff, ...). */
        public Jokers create(CreateSpec spec) { return effect(new Effect.Create(spec)); }

        /** Create one of {@code kind} (the common single-card case). */
        public Jokers create(CreateSpec.Kind kind) { return effect(new Effect.Create(new CreateSpec(kind))); }

        /** Permanently mutate each matching card — enhance/convert/add-chips (Hiker, Midas Mask, Vampire). */
        public Jokers mutateCard(CardMod mod) { return effect(new Effect.MutateCard(new Selector.Focus(), mod)); }

        /** Level up the played poker hand by {@code levels} (Space, Burnt). */
        public Jokers levelUpHand(int levels) {
            return effect(new Effect.LevelHands(Effect.LevelHands.Scope.PLAYED, new Value.Const(levels)));
        }

        /** Add a permanent copy of the scored card to the deck (DNA). */
        public Jokers copyScored() { return effect(new Effect.Copy(new com.balatro.grammar.Selector.Focus(), 1)); }

        /** Commit a rule with one or more {@link Effect}s (compound effects = several in order). */
        public Jokers effect(Effect... effects) {
            rules.add(new Rule(trigger, condition, java.util.List.of(effects)));
            return Jokers.this;
        }
    }

    /** A state mutation under construction; {@code .gain/.set/.reset} commits it as a {@link Effect.MutateState}
     *  rule — a write is just a rule whose one effect is {@code Modify(self.state)}. */
    public final class MutationBuilder {
        private final Trigger trigger;
        private Condition condition = new Condition.Always();

        MutationBuilder(Trigger trigger) { this.trigger = trigger; }

        public MutationBuilder when(Condition c) { this.condition = c; return this; }

        /** Add {@code by} to a state counter (Constellation: gain a planet; Canio: +1 xMult). */
        public Jokers gain(String var, double by) {
            return write(var, Effect.Operation.ADD, by, null, Effect.MutateState.Scope.SELF);
        }

        /** Add {@code by} per event-card matching {@code perCard} (Hit the Road: +0.5 per Jack discarded). */
        public Jokers gainPerCard(String var, double by, Condition perCard) {
            return write(var, Effect.Operation.ADD, by, perCard, Effect.MutateState.Scope.SELF);
        }

        /** Add {@code by} to this var on EVERY owned joker, not just self (Gift Card). */
        public Jokers gainEveryJoker(String var, double by) {
            return write(var, Effect.Operation.ADD, by, null, Effect.MutateState.Scope.ALL_JOKERS);
        }

        /** Set a state counter to {@code value}. */
        public Jokers set(String var, double value) {
            return write(var, Effect.Operation.SET, value, null, Effect.MutateState.Scope.SELF);
        }

        /** Reset the counter to 0 (Ride the Bus's streak breaking on a face card). */
        public Jokers reset(String var) {
            return write(var, Effect.Operation.SET, 0, null, Effect.MutateState.Scope.SELF); // reset = SET 0
        }

        private Jokers write(String var, Effect.Operation op, double by, Condition perCard,
                Effect.MutateState.Scope scope) {
            rules.add(new Rule(trigger, condition,
                    java.util.List.of(new Effect.MutateState(var, op, by, perCard, scope))));
            return Jokers.this;
        }
    }
}
