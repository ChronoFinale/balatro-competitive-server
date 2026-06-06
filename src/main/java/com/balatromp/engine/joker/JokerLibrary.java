package com.balatromp.engine.joker;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Suit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The starter joker set. Each one is deliberately chosen to exercise a distinct
 * path through the scoring pipeline, proving the engine handles every effect
 * shape (spec §1–§4). This is also the codegen source-of-truth: metadata here +
 * server-only logic, with client display generated separately (spec §6).
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
        register(GoldenJoker::new);
        register(FacelessJoker::new);
        register(Constellation::new);
    }

    private static void register(Supplier<Joker> factory) {
        REGISTRY.put(factory.get().key(), factory);
    }

    public static Joker create(String key) {
        Supplier<Joker> f = REGISTRY.get(key);
        if (f == null) throw new IllegalArgumentException("Unknown joker: " + key);
        return f.get();
    }

    public static Map<String, Supplier<Joker>> registry() {
        return REGISTRY;
    }

    // --- joker_main: flat +mult ------------------------------------------------
    public static final class PlainJoker implements Joker {
        public String key() { return "j_joker"; }
        public String name() { return "Joker"; }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.JOKER_MAIN) {
                return JokerEffect.mult(4).msg("+4 Mult");
            }
            return null;
        }
    }

    // --- on_scored: per-card conditional +mult --------------------------------
    public static final class GreedyJoker implements Joker {
        public String key() { return "j_greedy_joker"; }
        public String name() { return "Greedy Joker"; }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.ON_SCORED && ctx.scoredCard.isSuit(Suit.DIAMONDS)) {
                return JokerEffect.mult(3).msg("+3 Mult");
            }
            return null;
        }
    }

    // --- joker_main: conditional +chips on hand type --------------------------
    public static final class SlyJoker implements Joker {
        public String key() { return "j_sly_joker"; }
        public String name() { return "Sly Joker"; }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.JOKER_MAIN && ctx.handType.containsPair()) {
                return JokerEffect.chips(50).msg("+50 Chips");
            }
            return null;
        }
    }

    // --- joker_main: conditional on played-hand size --------------------------
    public static final class HalfJoker implements Joker {
        public String key() { return "j_half"; }
        public String name() { return "Half Joker"; }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.JOKER_MAIN && ctx.playedCards.size() <= 3) {
                return JokerEffect.mult(20).msg("+20 Mult");
            }
            return null;
        }
    }

    // --- on_scored: per-card rank predicate -----------------------------------
    public static final class EvenSteven implements Joker {
        public String key() { return "j_even_steven"; }
        public String name() { return "Even Steven"; }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.ON_SCORED && !ctx.scoredCard.isStone()
                    && ctx.scoredCard.rank.isEven()) {
                return JokerEffect.mult(4).msg("+4 Mult");
            }
            return null;
        }
    }

    // --- stateful: scales with persistent server-side state -------------------
    public static final class RideTheBus implements Joker {
        public String key() { return "j_ride_the_bus"; }
        public String name() { return "Ride the Bus"; }
        public JokerEffect calculate(EvaluationContext ctx) {
            // Update the streak once per real hand (not on Blueprint copies).
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
        public String key() { return "j_hack"; }
        public String name() { return "Hack"; }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.REPETITION_PLAYED && !ctx.scoredCard.isStone()) {
                int id = ctx.scoredCard.id();
                if (id >= 2 && id <= 5) {
                    return JokerEffect.repetitions(1).msg("Retrigger");
                }
            }
            return null;
        }
    }

    // --- lifecycle: END_OF_ROUND economy -------------------------------------
    public static final class GoldenJoker implements Joker {
        public String key() { return "j_golden"; }
        public String name() { return "Golden Joker"; }
        public JokerEffect calculate(EvaluationContext ctx) {
            if (ctx.phase == Trigger.END_OF_ROUND) {
                return JokerEffect.dollars(4).msg("+$4");
            }
            return null;
        }
    }

    // --- lifecycle: PRE_DISCARD reacts to the discarded set -------------------
    public static final class FacelessJoker implements Joker {
        public String key() { return "j_faceless"; }
        public String name() { return "Faceless Joker"; }
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
        public String key() { return "j_constellation"; }
        public String name() { return "Constellation"; }
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
        public String key() { return "j_blueprint"; }
        public String name() { return "Blueprint"; }
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
}
