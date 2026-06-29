package com.balatro.engine.eval;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.state.RoundTargets;
import com.balatro.grammar.Condition;

/**
 * The interpreter for the {@link Condition} grammar — tests a predicate node against an
 * {@link EvaluationContext}. Lives in the engine; the {@link Condition} types stay pure data. Every case is
 * null-safe (a predicate reading a context field absent for the trigger returns false), exactly as the old
 * {@code Condition.test} methods were — only the dispatch moved here.
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {}

    /** Face-ness honouring Pareidolia ({@code ctx.allFaces}); Stone cards never count. */
    static boolean isFace(EvaluationContext ctx, Card c) {
        return c != null && !c.isStone() && (c.isFace() || ctx.allFaces);
    }

    /** The CALCULATING joker's per-round rolled value for {@code domain} (Suit/Integer rank/HandType), or
     *  null if there's no self or it hasn't rolled one. The {@code *IsTarget} conditions read their own joker. */
    private static Object rolledTarget(EvaluationContext ctx, RoundTargets.Domain domain) {
        if (ctx.run == null || ctx.jokers == null
                || ctx.selfIndex < 0 || ctx.selfIndex >= ctx.jokers.size()) {
            return null;
        }
        return ctx.run.roundTargets.get(RoundTargets.key(ctx.self().key(), domain));
    }

    /** The comparison test for {@code Cmp} — interpretation of the pure-data grammar enum lives here. */
    private static boolean holds(Condition.Cmp cmp, double value, double target) {
        return switch (cmp) {
            case LTE -> value <= target;
            case GTE -> value >= target;
            case EQ -> value == target;
        };
    }

    public static boolean test(Condition cond, EvaluationContext ctx) {
        return switch (cond) {
            case Condition.Always ignored -> true;
            case Condition.ScoredSuit sc -> {
                Card c = ctx.scoredCard;
                yield c != null && sc.suit() != null && c.isSuit(sc.suit());
            }
            case Condition.ScoredSuitIsTarget ignored -> {
                Card c = ctx.scoredCard;
                yield c != null && rolledTarget(ctx, RoundTargets.Domain.SUIT) instanceof Suit s && c.isSuit(s);
            }
            case Condition.ScoredParity p -> {
                Card c = ctx.scoredCard;
                if (c == null || c.isStone()) yield false;
                int id = c.id();
                boolean isEven = id == 2 || id == 4 || id == 6 || id == 8 || id == 10;
                boolean isOdd = id == 3 || id == 5 || id == 7 || id == 9 || id == 14; // Ace = odd
                yield p.parity() == Condition.Parity.EVEN ? isEven : isOdd;
            }
            case Condition.ScoredIsFace ignored -> isFace(ctx, ctx.scoredCard);
            case Condition.ScoredPlayedThisAnte ignored -> ctx.scoredCard != null && ctx.scoredCard.playedThisAnte;
            case Condition.ScoredRankBetween rb -> {
                Card c = ctx.scoredCard;
                if (c == null || c.isStone()) yield false;
                int id = c.id();
                yield id >= rb.min() && id <= rb.max();
            }
            case Condition.ScoredFirst ignored -> ctx.scoredCard != null && ctx.scoringCards != null
                    && !ctx.scoringCards.isEmpty() && ctx.scoringCards.get(0) == ctx.scoredCard;
            case Condition.ScoredEnhancement se ->
                    ctx.scoredCard != null && ctx.scoredCard.enhancement == se.enhancement();
            case Condition.HandContainsPair ignored -> ctx.handType != null && ctx.handType.containsPair();
            case Condition.HandContains hc -> ctx.handType != null && ctx.handType.contains(hc.hand());
            case Condition.HandIs hi -> ctx.handType != null && hi.hand() != null && ctx.handType == hi.hand();
            case Condition.HandIsTarget ignored ->
                    ctx.handType != null && rolledTarget(ctx, RoundTargets.Domain.HAND_TYPE) instanceof HandType h
                            && ctx.handType == h;
            case Condition.PlayedCount pc -> ctx.playedCards != null && holds(pc.cmp(), ctx.playedCards.size(), pc.n());
            case Condition.DiscardedFaceCount df -> {
                if (ctx.eventCards == null) yield false;
                int faces = 0;
                for (Card c : ctx.eventCards) if (c.isFace()) faces++;
                yield faces >= df.min();
            }
            case Condition.ScoringAnyFace ignored -> {
                if (ctx.scoringCards == null) yield false;
                boolean any = false;
                for (Card c : ctx.scoringCards) if (isFace(ctx, c)) { any = true; break; }
                yield any;
            }
            case Condition.ScoredAmongFirst af -> {
                if (ctx.scoredCard == null || ctx.scoringCards == null) yield false;
                int idx = ctx.scoringCards.indexOf(ctx.scoredCard);
                yield idx >= 0 && idx < af.n();
            }
            case Condition.ScoredFirstFace ignored -> {
                if (!isFace(ctx, ctx.scoredCard) || ctx.scoringCards == null) yield false;
                boolean res = false;
                for (Card c : ctx.scoringCards) {
                    if (isFace(ctx, c)) { res = (c == ctx.scoredCard); break; } // first face must be this one
                }
                yield res;
            }
            case Condition.Compare cp -> holds(cp.cmp(), ValueResolver.resolve(cp.value(), ctx), cp.threshold());
            case Condition.HeldAllSuits ha -> {
                if (ctx.heldCards == null) yield true;
                boolean ok = true;
                for (Card c : ctx.heldCards) {
                    boolean match = false;
                    for (Suit s : ha.suits()) if (c.isSuit(s)) { match = true; break; }
                    if (!match) { ok = false; break; }
                }
                yield ok;
            }
            case Condition.ScoringContainsSuit sc -> {
                if (ctx.scoringCards == null) yield false;
                boolean any = false;
                for (Card c : ctx.scoringCards) if (c.isSuit(sc.suit())) { any = true; break; }
                yield any;
            }
            case Condition.ScoredRankIsTarget ignored -> {
                Card c = ctx.scoredCard;
                yield c != null && !c.isStone()
                        && rolledTarget(ctx, RoundTargets.Domain.RANK) instanceof Integer r && c.id() == r;
            }
            case Condition.InPvpBlind ignored -> ctx.run != null && ctx.run.inPvpBlind;
            case Condition.ReachedPvpFirst ignored -> ctx.reachedPvpFirst;
            case Condition.HandsSinceAcquire hs -> {
                if (ctx.run == null) yield false;
                int acq = ((Number) ctx.selfState().getOrDefault("acqHands", 0)).intValue();
                yield ctx.run.handsPlayedTotal - acq < hs.max();
            }
            case Condition.RunVarModulo rm -> ctx.run != null && rm.mod() != 0
                    && Math.floorMod((long) ValueResolver.readVar(rm.which(), ctx), rm.mod()) == rm.remainder();
            case Condition.OtherJokerRarity oj ->
                    ctx.otherJoker != null && oj.rarity() == ctx.otherJoker.info().rarity();
            case Condition.HandPlayedThisRound ignored -> ctx.run != null && ctx.handType != null
                    && ctx.run.handTypesThisRound.contains(ctx.handType);
            case Condition.RoundHandTypeConsistent ignored -> {
                if (ctx.run == null || ctx.handType == null) yield true;
                var played = ctx.run.handTypesThisRound;
                yield played.isEmpty() || played.contains(ctx.handType);
            }
            case Condition.PlayedHandIsMostPlayed ignored -> {
                if (ctx.run == null || ctx.handType == null) yield false;
                int mine = ctx.run.handTypePlays.getOrDefault(ctx.handType, 0);
                if (mine == 0) yield false;
                int max = ctx.run.handTypePlays.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                yield mine == max;
            }
            case Condition.BossDefeated ignored -> ctx.bossDefeated;
            case Condition.BossBlindSelected ignored -> ctx.bossBlind;
            case Condition.BossAbilityActive ignored -> ctx.run != null && ctx.run.bossHasActiveAbility;
            case Condition.ConsumableType ct -> ct.consumable() == ctx.consumableType;
            case Condition.Chance ch -> {
                if (ctx.preview) yield false; // preview shows the guaranteed floor — a gate never procs
                int probMult = ctx.run != null ? ctx.run.probabilityNumerator : 1;
                double roll = switch (ch.gate()) {
                    case Condition.RngGate.SharedProb sp -> ctx.nextProb(sp.seedKey());
                    case Condition.RngGate.DedicatedStream ds ->
                            ctx.nextProbOn(com.balatro.engine.rng.RngSource.of(ds.name()).pvpPerHand());
                };
                yield roll < (double) (ch.odds().numerator() * probMult) / ch.odds().denominator();
            }
            case Condition.And a -> {
                boolean all = true;
                for (Condition c : a.all()) if (!test(c, ctx)) { all = false; break; }
                yield all;
            }
            case Condition.Or o -> {
                boolean any = false;
                for (Condition c : o.any()) if (test(c, ctx)) { any = true; break; }
                yield any;
            }
            case Condition.Not n -> !test(n.inner(), ctx);
        };
    }
}
