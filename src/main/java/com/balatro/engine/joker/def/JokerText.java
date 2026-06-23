package com.balatro.engine.joker.def;


import com.balatro.engine.joker.Trigger;

/**
 * Proof-of-concept: generate a joker's description <i>from its rules</i>, the same way {@code preview.js}
 * computes its score from its rules. The description then can't drift from the effect — it <b>is</b> the
 * effect, rendered. Handles the common shapes (constant scoring + a simple condition/quantifier); returns
 * {@code null} for anything it can't yet express, so callers fall back to the authored text. If this proves
 * out, it grows to cover the full {@link Effect}/{@link Condition} vocabulary and the authored descs go away.
 */
public final class JokerText {

    private JokerText() {}

    /** Generated description, or {@code null} if the rules use a shape this PoC doesn't render yet. */
    public static String describe(JokerDef def) {
        if (def.rules().size() != 1) return null;            // PoC: single-rule jokers only
        Rule r = def.rules().get(0);
        if (r.effects().size() != 1) return null;
        if (!(r.effects().get(0) instanceof Effect.Score score)) return null;
        String effect = effect(score);
        if (effect == null) return null;

        return switch (r.when()) {
            case JOKER_MAIN -> {
                String cond = handCond(r.condition());
                yield cond == null ? null : (cond.isEmpty() ? effect : effect + " if " + cond);
            }
            case ON_SCORED -> {
                String each = cardCond(r.condition());
                yield each == null ? null : "Each played " + each + " gives " + effect;
            }
            case END_OF_ROUND -> isAlways(r.condition()) ? effect + " at end of round" : null;
            default -> null;
        };
    }

    private static String effect(Effect.Score s) {
        if (!(s.value() instanceof Value.Const c)) return null; // PoC: constant magnitudes only
        String n = num(c.amount());
        return switch (s.term()) {
            case CHIPS -> "+" + n + " Chips";
            case DOLLARS -> "+$" + n;
            case MULT -> switch (s.op()) {
                case ADD -> "+" + n + " Mult";
                case MULTIPLY -> "x" + n + " Mult";
                case POWER -> "^" + n + " Mult";
                default -> null;
            };
            default -> null;
        };
    }

    /** A whole-played-hand condition → "the played hand contains a Pair" / "3 or fewer cards are played". */
    private static String handCond(Condition c) {
        if (isAlways(c)) return "";
        if (c instanceof Condition.HandContainsPair) return "the played hand contains a Pair";
        if (c instanceof Condition.HandContains h) return "the played hand contains a " + h.hand().display;
        if (c instanceof Condition.PlayedCount p) {
            return switch (p.cmp()) {
                case LTE -> p.n() + " or fewer cards are played";
                case GTE -> p.n() + " or more cards are played";
                case EQ -> "exactly " + p.n() + " cards are played";
                default -> null;
            };
        }
        return null;
    }

    /** A per-scored-card condition → "even-rank card" / "face card" / "Heart". */
    private static String cardCond(Condition c) {
        if (isAlways(c)) return "card";
        if (c instanceof Condition.ScoredParity pa) return (pa.even() ? "even" : "odd") + "-rank card";
        if (c instanceof Condition.ScoredIsFace) return "face card";
        if (c instanceof Condition.ScoredSuit s && s.suit() != null) return suit(s.suit()) + " card";
        return null;
    }

    private static boolean isAlways(Condition c) {
        return c instanceof Condition.Always;
    }

    private static String suit(com.balatro.engine.card.Suit s) {
        return switch (s) {
            case HEARTS -> "Heart";
            case SPADES -> "Spade";
            case CLUBS -> "Club";
            case DIAMONDS -> "Diamond";
        };
    }

    private static String num(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
