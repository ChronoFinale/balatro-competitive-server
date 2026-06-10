package com.balatromp.engine.joker.def;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Enhancement;
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
    @JsonSubTypes.Type(value = Value.StateStep.class, name = "stateStep"),
    @JsonSubTypes.Type(value = Value.Count.class, name = "count"),
    @JsonSubTypes.Type(value = Value.RunVar.class, name = "runVar"),
    @JsonSubTypes.Type(value = Value.RunVarStep.class, name = "runVarStep"),
    @JsonSubTypes.Type(value = Value.Stat.class, name = "stat"),
    @JsonSubTypes.Type(value = Value.HeldExtreme.class, name = "heldExtreme"),
    @JsonSubTypes.Type(value = Value.DeckRankCount.class, name = "deckRankCount"),
    @JsonSubTypes.Type(value = Value.Clamp.class, name = "clamp"),
    @JsonSubTypes.Type(value = Value.HandTypePlays.class, name = "handTypePlays"),
    @JsonSubTypes.Type(value = Value.OtherJokersSellSum.class, name = "otherJokersSellSum"),
    @JsonSubTypes.Type(value = Value.Random.class, name = "random"),
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

    /** {@code base + scale * (sum of OTHER owned jokers' sell value, max(1, cost/2))} — Swashbuckler. */
    record OtherJokersSellSum(double base, double scale) implements Value {
        public double resolve(EvaluationContext ctx) {
            if (ctx.jokers == null) return base;
            int sum = 0;
            for (int i = 0; i < ctx.jokers.size(); i++) {
                if (i == ctx.selfIndex) continue;
                sum += Math.max(1, ctx.jokers.get(i).info().cost() / 2);
            }
            return base + scale * sum;
        }
    }

    /** {@code base + scale * (times the current poker hand has been played this run)} — Supernova. */
    record HandTypePlays(double base, double scale) implements Value {
        public double resolve(EvaluationContext ctx) {
            if (ctx.run == null || ctx.handType == null) return base;
            return base + scale * ctx.run.handTypePlays.getOrDefault(ctx.handType, 0);
        }
    }

    /** Clamps an inner value to {@code [min, max]} — decay jokers floor at 0/1 (Ice Cream, Ramen). */
    record Clamp(Value inner, double min, double max) implements Value {
        public double resolve(EvaluationContext ctx) {
            return Math.max(min, Math.min(max, inner.resolve(ctx)));
        }
    }

    /** {@code base + scale * floor(state / per)} — stepwise state scaling (Yorick: x1 per 23 discarded). */
    record StateStep(String var, double base, double scale, double per) implements Value {
        public double resolve(EvaluationContext ctx) {
            if (per == 0) return base;
            Object v = ctx.selfState().getOrDefault(var, 0);
            double n = (v instanceof Number num) ? num.doubleValue() : 0;
            return base + scale * Math.floor(n / per);
        }
    }

    /** Which set of cards a {@link Count} scans. */
    enum Source { PLAYED, SCORING, HELD, EVENT }

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
                case EVENT -> ctx.eventCards; // discarded set (PRE_DISCARD), etc.
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
    enum Var { MONEY, HANDS_LEFT, DISCARDS_LEFT, HAND_SIZE, ANTE, DISCARDS_USED, HANDS_PLAYED,
        HANDS_PLAYED_TOTAL, ROUNDS_PLAYED, CARDS_DISCARDED_TOTAL, LUCKY_TRIGGERS, UNIQUE_PLANETS }

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
                case DISCARDS_USED -> ctx.run.discardsUsedThisRound;
                case HANDS_PLAYED -> ctx.run.handsPlayedThisRound;
                case HANDS_PLAYED_TOTAL -> ctx.run.handsPlayedTotal;
                case ROUNDS_PLAYED -> ctx.run.roundsPlayedTotal;
                case CARDS_DISCARDED_TOTAL -> ctx.run.cardsDiscardedTotal;
                case LUCKY_TRIGGERS -> ctx.run.luckyTriggersTotal;
                case UNIQUE_PLANETS -> ctx.run.planetsUsedThisRun.size();
            };
            return base + scale * v;
        }
    }

    /**
     * {@code base + scale * floor(runVar / per)} — stepwise scaling (Bootstraps:
     * +2 Mult per $5 → base 0, scale 2, per 5 over MONEY).
     */
    record RunVarStep(Var which, double base, double scale, double per) implements Value {
        public double resolve(EvaluationContext ctx) {
            if (ctx.run == null || per == 0) return base;
            double v = switch (which) {
                case MONEY -> ctx.run.money;
                case HANDS_LEFT -> ctx.run.handsLeft;
                case DISCARDS_LEFT -> ctx.run.discardsLeft;
                case HAND_SIZE -> ctx.run.handSize;
                case ANTE -> ctx.run.ante;
                case DISCARDS_USED -> ctx.run.discardsUsedThisRound;
                case HANDS_PLAYED -> ctx.run.handsPlayedThisRound;
                case HANDS_PLAYED_TOTAL -> ctx.run.handsPlayedTotal;
                case ROUNDS_PLAYED -> ctx.run.roundsPlayedTotal;
                case CARDS_DISCARDED_TOTAL -> ctx.run.cardsDiscardedTotal;
                case LUCKY_TRIGGERS -> ctx.run.luckyTriggersTotal;
                case UNIQUE_PLANETS -> ctx.run.planetsUsedThisRun.size();
            };
            return base + scale * Math.floor(v / per);
        }
    }

    /**
     * {@code base + scale * (lowest|highest base-chip value among held cards)} —
     * Raised Fist (double the lowest held card's rank). Stone cards are ignored; an
     * empty hand resolves to {@code base}.
     */
    record HeldExtreme(boolean lowest, double base, double scale) implements Value {
        public double resolve(EvaluationContext ctx) {
            if (ctx.heldCards == null) return base;
            int extreme = lowest ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            boolean found = false;
            for (Card c : ctx.heldCards) {
                if (c.isStone()) continue;
                int v = c.baseChips();
                extreme = lowest ? Math.min(extreme, v) : Math.max(extreme, v);
                found = true;
            }
            return found ? base + scale * extreme : base;
        }
    }

    /**
     * {@code base + scale * (cards of rank id {@code rankId} in the full deck)} —
     * Cloud 9 ($1 per 9 in the deck). Reads the persistent deck composition.
     */
    record DeckRankCount(int rankId, double base, double scale) implements Value {
        public double resolve(EvaluationContext ctx) {
            if (ctx.run == null) return base;
            long n = ctx.run.deckComposition.stream().filter(c -> c.id() == rankId).count();
            return base + scale * n;
        }
    }

    /**
     * A uniform random integer magnitude in {@code [min, max]} (Misprint 0..23),
     * popped from a game-long queue keyed by {@code seedKey} so both players roll
     * the same sequence.
     */
    record Random(double min, double max, String seedKey) implements Value {
        public double resolve(EvaluationContext ctx) {
            double roll = ctx.nextProb(seedKey);
            return min + Math.floor(roll * (max - min + 1));
        }
    }

    /** Which deck/run aggregate a {@link Stat} reads. */
    enum Which { DECK_SIZE, DECK_REMAINING, ENHANCED_CARD_COUNT, DECK_ENH_COUNT, OWNED_JOKERS,
        EMPTY_JOKER_SLOTS, CARDS_BELOW_FULL }

    /** A standard full deck size — Erosion's reference point. */
    int FULL_DECK = 52;

    /**
     * {@code base + scale * (deck/run aggregate)} — Blue (deck remaining), Abstract
     * (jokers owned), Stone/Steel (cards of an enhancement in the full deck), etc.
     * Reads {@code RunState} (the persistent deckComposition / jokers); pure and
     * deterministic. {@code enhancement} is used only by DECK_ENH_COUNT.
     */
    record Stat(Which which, double base, double scale, Enhancement enhancement) implements Value {
        public double resolve(EvaluationContext ctx) {
            if (ctx.run == null) return base;
            long n = switch (which) {
                case DECK_SIZE -> ctx.run.deckComposition.size();
                case DECK_REMAINING -> ctx.run.deck != null ? ctx.run.deck.remaining() : 0;
                case ENHANCED_CARD_COUNT -> ctx.run.deckComposition.stream()
                        .filter(c -> c.enhancement != Enhancement.NONE).count();
                case DECK_ENH_COUNT -> ctx.run.deckComposition.stream()
                        .filter(c -> c.enhancement == enhancement).count();
                case OWNED_JOKERS -> ctx.run.jokers().size();
                case EMPTY_JOKER_SLOTS -> Math.max(0, 5 - ctx.run.jokers().size());
                case CARDS_BELOW_FULL -> Math.max(0, FULL_DECK - ctx.run.deckComposition.size());
            };
            return base + scale * n;
        }
    }
}
