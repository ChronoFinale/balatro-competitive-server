package com.balatro.engine.eval;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.grammar.Condition;
import com.balatro.grammar.Property;
import com.balatro.grammar.Value;
import java.util.List;

/**
 * The interpreter for the {@link Value} grammar — resolves a value node to a number against an
 * {@link EvaluationContext}. Lives in the engine (it reads {@code RunState}/RNG/cards via the context); the
 * {@link Value} types themselves stay pure data with no behaviour, so the grammar is a leaf with no runtime
 * dependency. Behaviour-identical to the old {@code Value.resolve} methods — only the dispatch moved here.
 */
public final class ValueResolver {

    private ValueResolver() {}

    public static double resolve(Value v, EvaluationContext ctx) {
        return switch (v) {
            case Value.Const c -> c.amount();
            case Value.Prop p -> ctx.selfProp(p.name());
            case Value.State s -> {
                Object val = ctx.selfState().getOrDefault(s.var(), 0);
                double n = (val instanceof Number num) ? num.doubleValue() : 0;
                yield s.base() + s.scale() * n;
            }
            case Value.OtherJokersSellSum o -> {
                if (ctx.jokers == null) yield o.base();
                int sum = 0;
                for (int i = 0; i < ctx.jokers.size(); i++) {
                    if (i == ctx.selfIndex) continue;
                    sum += Math.max(1, ctx.jokers.get(i).info().cost() / 2);
                }
                yield o.base() + o.scale() * sum;
            }
            case Value.HandTypePlays h -> {
                if (ctx.run == null || ctx.handType == null) yield h.base();
                yield h.base() + h.scale() * ctx.run.handTypePlays.getOrDefault(ctx.handType, 0);
            }
            case Value.Clamp cl -> Math.max(cl.min(), Math.min(cl.max(), resolve(cl.inner(), ctx)));
            case Value.Diff d -> resolve(d.left(), ctx) - resolve(d.right(), ctx);
            case Value.StateStep s -> {
                if (s.per() == 0) yield s.base();
                Object val = ctx.selfState().getOrDefault(s.var(), 0);
                double n = (val instanceof Number num) ? num.doubleValue() : 0;
                yield s.base() + s.scale() * Math.floor(n / s.per());
            }
            case Value.Count co -> {
                List<Card> cards = switch (co.source()) {
                    case PLAYED -> ctx.playedCards;
                    case SCORING -> ctx.scoringCards;
                    case HELD -> ctx.heldCards;
                    case EVENT -> ctx.eventCards;
                };
                if (cards == null) yield co.base();
                Card prev = ctx.scoredCard;
                int n = 0;
                for (Card card : cards) {
                    ctx.scoredCard = card;
                    if (ConditionEvaluator.test(co.match(), ctx)) n++; // Condition still self-evaluates (extracted in a later step)
                }
                ctx.scoredCard = prev;
                yield co.base() + co.scale() * n;
            }
            case Value.RunVar rv -> ctx.run == null ? rv.base() : rv.base() + rv.scale() * readVar(rv.which(), ctx);
            case Value.RunVarStep rs ->
                    (ctx.run == null || rs.per() == 0) ? rs.base()
                            : rs.base() + rs.scale() * Math.floor(readVar(rs.which(), ctx) / rs.per());
            case Value.HeldExtreme he -> {
                if (ctx.heldCards == null) yield he.base();
                boolean lowest = he.end() == Value.Extreme.LOWEST;
                int extreme = lowest ? Integer.MAX_VALUE : Integer.MIN_VALUE;
                boolean found = false;
                for (Card c : ctx.heldCards) {
                    if (c.isStone()) continue;
                    int val = c.baseChips();
                    extreme = lowest ? Math.min(extreme, val) : Math.max(extreme, val);
                    found = true;
                }
                yield found ? he.base() + he.scale() * extreme : he.base();
            }
            case Value.DeckRankCount dr -> {
                if (ctx.run == null) yield dr.base();
                long n = ctx.run.deckComposition.stream().filter(c -> c.id() == dr.rankId()).count();
                yield dr.base() + dr.scale() * n;
            }
            case Value.Random r -> {
                if (ctx.preview) yield r.min(); // preview shows the guaranteed floor (the minimum magnitude)
                double roll = ctx.nextProb(r.seedKey());
                yield r.min() + Math.floor(roll * (r.max() - r.min() + 1));
            }
            case Value.Stat st -> {
                if (ctx.run == null) yield st.base();
                long n = switch (st.which()) {
                    case DECK_REMAINING -> ctx.run.deck != null ? ctx.run.deck.remaining() : 0;
                    case ENHANCED_CARD_COUNT -> ctx.run.deckComposition.stream()
                            .filter(c -> c.enhancement != Enhancement.NONE).count();
                    case DECK_ENH_COUNT -> ctx.run.deckComposition.stream()
                            .filter(c -> c.enhancement == st.enhancement()).count();
                    case OWNED_JOKERS -> ctx.run.jokers().size();
                    case EMPTY_JOKER_SLOTS -> Math.max(0, 5 - ctx.run.jokers().size());
                    case CARDS_BELOW_FULL -> Math.max(0, Value.FULL_DECK - ctx.run.deckComposition.size());
                };
                yield st.base() + st.scale() * n;
            }
        };
    }

    /** Read a live run-state quantity (shared by RunVar/RunVarStep and {@code Condition.RunVarModulo}). The
     *  {@link Property}→{@code RunState} mapping lives in one place. */
    public static double readVar(Property which, EvaluationContext ctx) {
        if (which instanceof com.balatro.grammar.Hand h) {
            return switch (h) {
                case SIZE -> ctx.run.handSize;
                case PLAYS -> ctx.run.handsLeft;
                case DISCARDS -> ctx.run.discardsLeft;
                case DRAW_COUNT -> throw new UnsupportedOperationException("DRAW_COUNT is write-only");
            };
        }
        return switch ((Value.Var) which) {
            case MONEY -> ctx.run.money;
            case CONSUMABLE_SLOTS -> ctx.run.consumableSlots;
            case JOKER_SLOTS -> ctx.run.jokerSlots;
            case ANTE -> ctx.run.ante;
            case DISCARDS_USED -> ctx.run.discardsUsedThisRound;
            case HANDS_PLAYED -> ctx.run.handsPlayedThisRound;
            case HANDS_PLAYED_TOTAL -> ctx.run.handsPlayedTotal;
            case ROUNDS_PLAYED -> ctx.run.roundsPlayedTotal;
            case CARDS_DISCARDED_TOTAL -> ctx.run.cardsDiscardedTotal;
            case LUCKY_TRIGGERS -> ctx.run.luckyTriggersTotal;
            case UNIQUE_PLANETS -> ctx.run.planetsUsedThisRun.size();
            case OBELISK_STREAK -> ctx.run.obeliskStreak;
            case BLINDS_SKIPPED -> ctx.run.blindsSkipped;
            case OPP_BLINDS_SKIPPED -> ctx.run.opponent.blindsSkipped;
            case OPP_LIVES_BEHIND -> Math.max(0, ctx.run.opponent.lives - ctx.run.myLives);
            case OPP_HANDS_LEFT -> ctx.run.opponent.handsLeft;
            case OPP_CARDS_SOLD -> ctx.run.opponent.cardsSold;
            case OPP_SHOP_SPENT -> ctx.run.opponent.shopSpentLastAnte;
            case GLASS_MULT -> ctx.run.capabilities.glassMult();
            case BLIND_PROGRESS -> ctx.run.blindProgress;
            case TOTAL_SELL_VALUE -> {
                int sell = 0;
                for (var j : ctx.run.jokers()) sell += Math.max(1, j.info().cost() / 2) + ctx.run.jokerInt(j, "sellBonus", 0);
                yield sell;
            }
            default -> throw new UnsupportedOperationException("not a readable run variable: " + which);
        };
    }
}
