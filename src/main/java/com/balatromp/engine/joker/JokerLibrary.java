package com.balatromp.engine.joker;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Suit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The starter joker set. Each one exercises a distinct path through the scoring
 * pipeline (spec §1–§4) and carries display metadata ({@link JokerInfo}:
 * description/rarity/cost/sprite). This is the codegen source-of-truth: metadata
 * here + server-only logic, with client display generated from {@code info()}.
 *
 * Sprite positions are Balatro's atlas cells (game.lua), used only if the local
 * Jokers atlas is present; no art is shipped.
 */
public final class JokerLibrary {

    private JokerLibrary() {}

    private static final Map<String, Supplier<Joker>> REGISTRY = new LinkedHashMap<>();

    static {
        register(PlainJoker::new);
        register(GreedyJoker::new);
        register(SlyJoker::new);
        register(HalfJoker::new);
        register(EvenSteven::new);
        register(RideTheBus::new);
        register(Hack::new);
        register(Blueprint::new);
        register(Brainstorm::new);
        register(GoldenJoker::new);
        register(FacelessJoker::new);
        register(Constellation::new);

        // Data-driven built-ins (real Balatro jokers as pure JokerDef data). Registered
        // here, BEFORE the BUILTIN_KEYS snapshot, so they join the curated shop pool and
        // score through the same authoritative path as the hand-coded set above.
        for (com.balatromp.engine.joker.def.JokerDef def
                : com.balatromp.engine.joker.def.BuiltinJokerDefs.all()) {
            REGISTRY.put(def.key(), () -> new com.balatromp.engine.joker.def.DataJoker(def));
        }
    }

    /**
     * The curated, hand-coded joker keys — the default competitive shop pool,
     * captured once at class init so later {@link #registerDef} calls (custom
     * builder jokers) never leak into the standard shop or perturb its
     * determinism. Custom jokers enter a shop only when a ruleset's pool opts
     * them in.
     */
    private static final java.util.List<String> BUILTIN_KEYS = java.util.List.copyOf(REGISTRY.keySet());

    public static java.util.List<String> builtinKeys() {
        return BUILTIN_KEYS;
    }

    /**
     * Jokers banned in Standard Ranked multiplayer (boss-blind interactions: Mr. Bones, Luchador,
     * Matador, Chicot). Excluded from every pool the shop, packs, AND creation effects draw from — not
     * merely skipped — so they can't be acquired at all in an MP run. The single source of truth.
     */
    public static final java.util.Set<String> MP_BANNED =
            java.util.Set.of("j_chicot", "j_matador", "j_mr_bones", "j_luchador");

    /** Built-in joker keys of a given rarity (e.g. "Common") — Riff Raff's creation pool. */
    public static java.util.List<String> keysByRarity(String rarity) {
        return BUILTIN_KEYS.stream()
                .filter(k -> rarity.equals(create(k).info().rarity()))
                .toList();
    }

    private static void register(Supplier<Joker> factory) {
        REGISTRY.put(factory.get().key(), factory);
    }

    /**
     * Register a data-driven joker (from {@link com.balatromp.engine.joker.def.JokerDef})
     * so it flows through {@link #create(String)} into shops exactly like a
     * hand-coded one. This is how custom jokers authored in the builder enter the
     * game: validated def in, a server-side {@code DataJoker} factory registered.
     */
    public static void registerDef(com.balatromp.engine.joker.def.JokerDef def) {
        REGISTRY.put(def.key(), () -> new com.balatromp.engine.joker.def.DataJoker(def));
    }

    public static Joker create(String key) {
        Supplier<Joker> f = REGISTRY.get(key);
        if (f == null) throw new IllegalArgumentException("Unknown joker: " + key);
        return f.get();
    }

    // Variant defs: jokerKey -> variant -> def. A joker (e.g. Hanging Chad) can behave
    // differently in single-player vs multiplayer; the active ruleset names the variant.
    private static final Map<String, Map<String, com.balatromp.engine.joker.def.JokerDef>> VARIANTS =
            new LinkedHashMap<>();

    static {
        com.balatromp.engine.joker.def.BuiltinJokerDefs.variants()
                .forEach((variant, defs) -> defs.forEach(d -> registerVariant(variant, d)));
    }

    /** Register an alternate behavior for an existing joker key under a named variant. */
    public static void registerVariant(String variant, com.balatromp.engine.joker.def.JokerDef def) {
        VARIANTS.computeIfAbsent(def.key(), k -> new LinkedHashMap<>()).put(variant, def);
    }

    /** Create a joker, using {@code variant}'s behavior if one is registered, else the default. */
    public static Joker create(String key, String variant) {
        Map<String, com.balatromp.engine.joker.def.JokerDef> vs = VARIANTS.get(key);
        if (variant != null && vs != null && vs.containsKey(variant)) {
            return new com.balatromp.engine.joker.def.DataJoker(vs.get(variant));
        }
        return create(key);
    }

    public static Map<String, Supplier<Joker>> registry() {
        return REGISTRY;
    }

    // --- joker_main: flat +mult ------------------------------------------------
    public static final class PlainJoker implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_joker", "Joker", "+4 Mult", "Common", 2, 0, 0);
        }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.JOKER_MAIN) return JokerEffect.mult(4).msg("+4 Mult");
            return null;
        }
    }

    // --- on_scored: per-card conditional +mult --------------------------------
    public static final class GreedyJoker implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_greedy_joker", "Greedy Joker",
                    "Each played Diamond gives +3 Mult", "Common", 5, 6, 1);
        }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.ON_SCORED && ctx.scoredCard.isSuit(Suit.DIAMONDS)) {
                return JokerEffect.mult(3).msg("+3 Mult");
            }
            return null;
        }
    }

    // --- joker_main: conditional +chips on hand type --------------------------
    public static final class SlyJoker implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_sly_joker", "Sly Joker",
                    "+50 Chips if the played hand contains a Pair", "Common", 3, 0, 14);
        }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.JOKER_MAIN && ctx.handType.containsPair()) {
                return JokerEffect.chips(50).msg("+50 Chips");
            }
            return null;
        }
    }

    // --- joker_main: conditional on played-hand size --------------------------
    public static final class HalfJoker implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_half", "Half Joker",
                    "+20 Mult if 3 or fewer cards are played", "Common", 5, 7, 0);
        }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.JOKER_MAIN && ctx.playedCards.size() <= 3) {
                return JokerEffect.mult(20).msg("+20 Mult");
            }
            return null;
        }
    }

    // --- on_scored: per-card rank predicate -----------------------------------
    public static final class EvenSteven implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_even_steven", "Even Steven",
                    "Each played even-rank card gives +4 Mult", "Common", 4, 8, 3);
        }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.ON_SCORED && !ctx.scoredCard.isStone()) {
                int id = ctx.scoredCard.id(); // even ranks per Balatro: 10/8/6/4/2 (Ace is odd)
                if (id == 2 || id == 4 || id == 6 || id == 8 || id == 10) {
                    return JokerEffect.mult(4).msg("+4 Mult");
                }
            }
            return null;
        }
    }

    // --- stateful: scales with persistent server-side state -------------------
    public static final class RideTheBus implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_ride_the_bus", "Ride the Bus",
                    "+1 Mult per consecutive hand with no face card", "Common", 6, 1, 6);
        }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.BEFORE && ctx.blueprintDepth == 0) {
                boolean anyFace = false;
                for (Card c : ctx.scoringCards) {
                    if (c.isFace()) { anyFace = true; break; }
                }
                int streak = (int) ctx.selfState().getOrDefault("streak", 0);
                streak = anyFace ? 0 : streak + 1;
                ctx.selfState().put("streak", streak);
                return null;
            }
            if (ctx.phase == Trigger.JOKER_MAIN) {
                int streak = (int) ctx.selfState().getOrDefault("streak", 0);
                if (streak > 0) return JokerEffect.mult(streak).msg("+" + streak + " Mult");
            }
            return null;
        }
    }

    // --- retrigger: adds repetitions to matching played cards -----------------
    public static final class Hack implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_hack", "Hack",
                    "Retrigger each played 2, 3, 4, and 5", "Uncommon", 6, 5, 2);
        }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.REPETITION_PLAYED && !ctx.scoredCard.isStone()) {
                int id = ctx.scoredCard.id();
                if (id >= 2 && id <= 5) return JokerEffect.repetitions(1).msg("Retrigger");
            }
            return null;
        }
    }

    // --- lifecycle: END_OF_ROUND economy --------------------------------------
    public static final class GoldenJoker implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_golden", "Golden Joker", "+$4 at end of round", "Common", 6, 9, 2);
        }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.END_OF_ROUND) return JokerEffect.dollars(4).msg("+$4");
            return null;
        }
    }

    // --- lifecycle: PRE_DISCARD reacts to the discarded set -------------------
    public static final class FacelessJoker implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_faceless", "Faceless Joker",
                    "+$5 if 3 or more face cards are discarded at once", "Common", 4, 1, 11);
        }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.PRE_DISCARD && ctx.eventCards != null) {
                int faces = 0;
                for (Card c : ctx.eventCards) if (c.isFace()) faces++;
                if (faces >= 3) return JokerEffect.dollars(5).msg("+$5");
            }
            return null;
        }
    }

    // --- lifecycle state + scoring: scales on consumable use ------------------
    public static final class Constellation implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_constellation", "Constellation",
                    "Gains x0.1 Mult per Planet card used", "Uncommon", 6, 9, 10);
        }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.USE_CONSUMABLE && "Planet".equals(ctx.consumableType)) {
                int n = (int) ctx.selfState().getOrDefault("planets", 0) + 1;
                ctx.selfState().put("planets", n);
                return null;
            }
            if (ctx.phase == Trigger.JOKER_MAIN) {
                int n = (int) ctx.selfState().getOrDefault("planets", 0);
                if (n > 0) {
                    double x = 1.0 + 0.1 * n;
                    return JokerEffect.xMult(x).msg("x" + x + " Mult");
                }
            }
            return null;
        }
    }

    // --- meta: re-entrant copy of the joker to the right (spec §4) -------------
    public static final class Blueprint implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_blueprint", "Blueprint",
                    "Copies the ability of the Joker to the right", "Rare", 10, 0, 3);
        }
        public boolean blueprintCompatible() { return false; }
        public JokerEffect calculate(EvaluationContext ctx) {
            int right = ctx.selfIndex + 1;
            if (right >= ctx.jokers.size()) return null;
            if (ctx.blueprintDepth > ctx.jokers.size()) return null; // recursion guard
            Joker target = ctx.jokers.get(right);
            if (!target.blueprintCompatible()) return null;
            JokerEffect e = target.calculate(ctx.forCopy(right));
            if (e != null) e.source = name() + " -> " + target.name();
            return e;
        }
    }

    // --- meta: re-entrant copy of the leftmost joker (Brainstorm) --------------
    public static final class Brainstorm implements Joker {
        public JokerInfo info() {
            return new JokerInfo("j_brainstorm", "Brainstorm",
                    "Copies the ability of the leftmost Joker", "Rare", 10, 1, 14);
        }
        public boolean blueprintCompatible() { return false; }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.jokers.isEmpty()) return null;
            if (ctx.blueprintDepth > ctx.jokers.size()) return null; // recursion guard
            Joker target = ctx.jokers.get(0);
            if (target == this || !target.blueprintCompatible()) return null;
            JokerEffect e = target.calculate(ctx.forCopy(0));
            if (e != null) e.source = name() + " -> " + target.name();
            return e;
        }
    }
}
