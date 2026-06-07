package com.balatromp.engine.joker.def;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.joker.EvaluationContext;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * A number resolved at evaluation time — the magnitude half of a data effect.
 * Covers every scaling shape the current game uses: a flat {@link Const} (+4
 * Mult); a {@link State} counter that ramps over a run (Ride the Bus's streak,
 * Constellation's planets); a {@link Count} of cards matching a predicate (+X per
 * face card held); and a {@link RunVar} reading live run state (per dollar, per
 * remaining hand). All scaling shapes are {@code base + scale * n}, so the builder
 * exposes one consistent "base / scale / source" form.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Value.Const.class, name = "const"),
    @JsonSubTypes.Type(value = Value.State.class, name = "state"),
    @JsonSubTypes.Type(value = Value.Count.class, name = "count"),
    @JsonSubTypes.Type(value = Value.RunVar.class, name = "runVar"),
})
public sealed interface Value {

    double resolve(EvaluationContext ctx);

    /** A fixed amount. */
    record Const(double amount) implements Value {
        public double resolve(EvaluationContext ctx) {
            return amount;
        }
    }

    /** {@code base + scale * (server-only state variable)}. */
    record State(String var, double base, double scale) implements Value {
        public double resolve(EvaluationContext ctx) {
            Object v = ctx.selfState().getOrDefault(var, 0);
            double n = (v instanceof Number num) ? num.doubleValue() : 0;
            return base + scale * n;
        }
    }

    /** Which set of cards a {@link Count} scans. */
    enum Source { PLAYED, SCORING, HELD }

    /**
     * {@code base + scale * (number of cards in source matching match)}. The
     * predicate is evaluated per card by reusing the per-card {@link Condition}
     * vocabulary (the same "scored*" checks), so "each face card", "each Diamond",
     * "each Glass card" all compose without new building blocks.
     */
    record Count(Source source, Condition match, double base, double scale) implements Value {
        public double resolve(EvaluationContext ctx) {
            List<Card> cards = switch (source) {
                case PLAYED -> ctx.playedCards;
                case SCORING -> ctx.scoringCards;
                case HELD -> ctx.heldCards;
            };
            if (cards == null) return base;
            Card prev = ctx.scoredCard;
            int n = 0;
            for (Card card : cards) {
                ctx.scoredCard = card;
                if (match.test(ctx)) n++;
            }
            ctx.scoredCard = prev;
            return base + scale * n;
        }
    }

    /** Which live run-state quantity a {@link RunVar} reads. */
    enum Var { MONEY, HANDS_LEFT, DISCARDS_LEFT, HAND_SIZE, ANTE }

    /** {@code base + scale * (live run-state quantity)} (per dollar, per remaining hand, ...). */
    record RunVar(Var which, double base, double scale) implements Value {
        public double resolve(EvaluationContext ctx) {
            if (ctx.run == null) return base;
            double v = switch (which) {
                case MONEY -> ctx.run.money;
                case HANDS_LEFT -> ctx.run.handsLeft;
                case DISCARDS_LEFT -> ctx.run.discardsLeft;
                case HAND_SIZE -> ctx.run.handSize;
                case ANTE -> ctx.run.ante;
            };
            return base + scale * v;
        }
    }
}
