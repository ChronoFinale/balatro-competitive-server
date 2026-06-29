package com.balatro.engine.game;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.game.Blinds.BlindType;
import com.balatro.engine.eval.ConditionEvaluator;
import com.balatro.engine.eval.ValueResolver;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.engine.rng.RngSources;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.grammar.CreateSpec;
import com.balatro.grammar.Effect;
import com.balatro.grammar.Rule;
import com.balatro.grammar.Selector;
import com.balatro.grammar.Trigger;
import com.balatro.grammar.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The boss + joker run-loop rule dispatch — everything fired OUTSIDE the scoring pipeline (blind-select joker
 * destroyers, boss per-hand/pre-hand/blind-start rules, on-sell rules, shop-exit rules, tags). The action-side
 * twin of {@code ScoringEngine}: it resolves a rule's {@link Effect}s against the run via the run-loop
 * interpreter and mutates authoritative state through {@code Run}'s {@code apply(Command)} sink.
 *
 * <p>Extracted from {@code Run} (the orchestrator). It leans on Run's package-internal toolkit
 * ({@code apply}/{@code roll}/{@code pick}/{@code rngCtx}/{@code runLoopContext}/{@code applyWrite}/
 * {@code applyLevelHands}/{@code voucherFoldD}/{@code bossDisabled}/{@code bossHasAbility}) and, for the
 * tag-granting effects, still calls back to Run's tag methods ({@code grantTag}/{@code addTagVoucher}/
 * {@code addFreeJoker}/{@code addFreeEditionedJoker}) — those move to {@code RunTags} next, after which these
 * calls repoint there.
 */
final class RunLoopRules {

    private RunLoopRules() {}

    static void applyJokerDestroyers(Run r) {
        List<Joker> js = r.state.jokers();
        // Ceremonial Dagger (RIGHT_NEIGHBOR): any blind; eats its right neighbour, gains 2x its sell value as Mult.
        for (int i = 0; i < js.size(); i++) {
            Selector.OtherJoker d = jokerDestroyer(js.get(i));
            if (d == null || d.scope() != Selector.OtherJoker.Scope.RIGHT_NEIGHBOR || i + 1 >= js.size()) continue;
            if (r.state.jokerFlag(js.get(i + 1), "eternal")) continue; // eternal can't be eaten
            Joker victim = js.remove(i + 1);
            if (d.mode() == Selector.OtherJoker.Mode.STEAL_MULT) r.state.addJokerInt(js.get(i), "mult", 2 * Math.max(1, victim.info().cost() / 2));
        }
        if (r.blind == BlindType.BOSS) return; // random joker-eaters (Madness) don't trigger on boss blinds
        // Madness (RANDOM_OTHER): Small/Big only; the ×0.5 Mult rides a state-write rule, this is just "eat a joker".
        for (int i = 0; i < js.size(); i++) {
            Selector.OtherJoker d = jokerDestroyer(js.get(i));
            if (d == null || d.scope() != Selector.OtherJoker.Scope.RANDOM_OTHER) continue;
            List<Joker> others = new ArrayList<>();
            for (int k = 0; k < js.size(); k++) { // eternal jokers can't be destroyed -> excluded as targets
                if (k != i && !r.state.jokerFlag(js.get(k), "eternal")) others.add(js.get(k));
            }
            if (others.isEmpty()) continue;
            // Identity-based pick: which joker is destroyed depends on the set held, not its order.
            Joker victim = r.state.queues.pick(others, RngSources.MADNESS_DESTROY, r.rngCtx(), Joker::key, Run.JOKER_QUALITY);
            int vidx = js.indexOf(victim);
            js.remove(vidx);
            if (vidx < i) i--; // a joker before us was removed; stay aligned
        }
    }

    /** A joker's blind-select "eat a joker" intent as DATA — the {@link Selector.OtherJoker} of its
     *  {@code BLIND_SELECTED} {@code Destroy} rule, or null. The careful destruction stays engine machinery above. */
    private static Selector.OtherJoker jokerDestroyer(Joker j) {
        if (!(j instanceof DataJoker dj)) return null;
        for (Rule r : dj.def().rules()) {
            if (r.when() != Trigger.BLIND_SELECTED) continue;
            for (Effect e : r.effects()) {
                if (e instanceof Effect.Destroy d && d.selector() instanceof Selector.OtherJoker oj) return oj;
            }
        }
        return null;
    }

    static void refreshDebuffs(Run r) {
        boolean disabled = r.bossDisabled(); // Chicot turns off the boss's debuffs
        com.balatro.grammar.Condition debuff = (r.boss != null) ? r.boss.debuff() : null;
        for (Card c : r.state.hand) {
            c.debuffed = !disabled && debuff != null && testCardDebuff(r, debuff, c);
        }
        // Keep Matador's trigger condition current (recomputed when the boss is disabled mid-blind too).
        r.state.bossHasActiveAbility = r.boss != null && !disabled && r.bossHasAbility();
    }

    /** Evaluate a boss debuff condition for one card (reuses the shared {@link com.balatro.grammar.Condition}
     *  vocabulary — "Clubs don't score" is {@code card().suit(CLUBS)}). */
    static boolean testCardDebuff(Run r, com.balatro.grammar.Condition cond, Card c) {
        EvaluationContext ctx = new EvaluationContext();
        ctx.scoredCard = c;
        ctx.run = r.state;
        return ConditionEvaluator.test(cond, ctx);
    }

    static void raiseBossRules(Run r, Trigger trigger, Consumer<EvaluationContext> setup) {
        if (r.boss == null || r.bossDisabled() || r.boss.rules().isEmpty()) return;
        EvaluationContext ctx = r.runLoopContext();
        ctx.phase = trigger;
        if (setup != null) setup.accept(ctx);
        for (Rule rule : r.boss.rules()) {
            if (rule.when() != trigger) continue;
            if (rule.condition() != null && !ConditionEvaluator.test(rule.condition(), ctx)) continue;
            for (Effect e : rule.effects()) applyRunLoopEffect(r, e, ctx);
        }
    }

    /** Boss per-hand effects (Tooth / Ox / Arm / Hook), fired after a hand is played + scored. */
    static void applyBossOnHandPlayed(Run r, List<Card> playedCards, ScoreResult score) {
        raiseBossRules(r, Trigger.ON_HAND_PLAYED, ctx -> {
            ctx.playedCards = playedCards;
            ctx.handType = (score != null) ? score.handType() : null;
        });
    }

    /** The run-loop effect interpreter (the action-side twin of {@code ConsumableApply}): resolve a
     *  {@link Value} against the run and mutate authoritative state. Shared by bosses (per-hand/blind/sell
     *  rules) and tags (Speed/Handy/Garbage money gains) — any effect fired outside the scoring pipeline. */
    static void applyRunLoopEffect(Run r, Effect e, EvaluationContext ctx) {
        switch (e) {
            case Effect.DiscardRandomHeld d -> hookDiscardAndRefill(r, d.count()); // The Hook
            case Effect.FlipAndShuffleJokers ignored -> { // Amber Acorn (blind start)
                r.jokersHidden = true;
                List<Joker> js = r.state.jokers();
                for (int i = js.size() - 1; i > 0; i--) { // deterministic Fisher–Yates from the seeded stream
                    int k = (int) (r.roll(RngSources.BOSS_ACORN) * (i + 1));
                    if (k > i) k = i;
                    java.util.Collections.swap(js, i, k);
                }
            }
            case Effect.DisableRandomJoker ignored -> { // Crimson Heart (pre-hand)
                List<Joker> js = r.state.jokers();
                for (Joker j : js) r.state.jokerState(j).put("bossDisabled", false); // re-arm the rest
                if (!js.isEmpty()) {
                    int idx = (int) (r.roll(RngSources.BOSS_CRIMSON) * js.size());
                    if (idx >= js.size()) idx = js.size() - 1;
                    r.state.jokerState(js.get(idx)).put("bossDisabled", true);
                }
            }
            case Effect.DisableBoss ignored -> { // Verdant Leaf (boss) / Luchador (sold during a boss)
                if (r.boss != null) { r.luchadorDisabledBoss = true; refreshDebuffs(r); }
            }
            case Effect.AddPack ap -> // pack tags (Charm/Meteor/Buffoon/Standard/Ethereal)
                r.shopPacks.add(new PackCatalog.Pack(PackCatalog.Kind.valueOf(ap.kind().name()), PackCatalog.Size.valueOf(ap.size().name())));
            case Effect.Create cr -> { // run-loop create: a SHOP-destination joker lands in the next shop
                if (cr.spec() instanceof CreateSpec.Joker j && j.destination() == CreateSpec.Destination.SHOP) {
                    if (j.edition() == Edition.NONE) RunTags.addFreeJoker(r, j.rarity()); // Rare/Uncommon by rarity
                    else RunTags.addFreeEditionedJoker(r, j.edition());                   // Foil/Holo/Poly/Negative
                } else { // PLAYER: jokers/consumables/cards straight into the run's slots (Top-Up tag)
                    r.apply(new com.balatro.engine.exec.Command.Create(cr.spec()));
                }
            }
            case Effect.LevelHands lh -> { // Orbital tag (MOST_PLAYED) / The Arm (PLAYED, -1 = delevel)
                if (lh.target() == com.balatro.grammar.Side.SELF && lh.scope() == Effect.LevelHands.Scope.PLAYED) {
                    if (ctx.handType != null) // The Arm: delevel the just-played hand now (n may be negative)
                        r.apply(new com.balatro.engine.exec.Command.LevelHand(ctx.handType,
                                (int) Math.round(ValueResolver.resolve(lh.levels(), ctx))));
                } else {
                    r.applyLevelHands(lh, ctx); // OPPONENT routing + ALL/MOST_PLAYED, all in one place
                }
            }
            case Effect.AddShopVoucher ignored -> RunTags.addTagVoucher(r);   // Voucher tag
            case Effect.ShopFlag sf -> {                                      // Coupon / D6 tags
                if (sf.flag() == Effect.ShopFlag.Flag.COUPON) r.couponActive = true;
                else if (sf.flag() == Effect.ShopFlag.Flag.D6) r.d6Active = true;
            }
            case Effect.Write w -> r.applyWrite(w.mod(), ctx);               // Juggle (Hand.SIZE) / Tooth/Ox (MONEY)
            case Effect.Copy cp -> { // run-loop copies (the rounds-owned gate is now a Condition on the rule)
                if (cp.selector() instanceof Selector.RandomConsumable && !r.state.consumables.isEmpty()) {
                    // Perkeo (shop exit): a slot-cap-ignoring Negative copy of a random held consumable.
                    String dup = r.state.queues.pick(r.state.consumables, RngSources.PERKEO_DUP, r.rngCtx(), s -> s, (a, b) -> 0);
                    r.apply(new com.balatro.engine.exec.Command.CopyConsumable(dup, com.balatro.engine.exec.Command.CopyConsumable.SlotPolicy.IGNORE_CAP));
                } else if (cp.selector() instanceof Selector.RandomJoker
                        && !r.state.jokers().isEmpty() && r.state.jokers().size() < Shop.JOKER_SLOT_LIMIT) {
                    // Invisible Joker (on sell): copy a joker — the rightmost in MP (deterministic), else a
                    // random one by identity (INVISIBLE_DUP).
                    Joker source = r.ruleset.capabilities().duplicateRightmost()
                            ? r.state.jokers().get(r.state.jokers().size() - 1)
                            : r.state.queues.pick(r.state.jokers(), RngSources.INVISIBLE_DUP, r.rngCtx(), Joker::key, Run.JOKER_QUALITY);
                    r.apply(new com.balatro.engine.exec.Command.CopyJoker(source, null));
                }
            }
            case Effect.CreateTag ct -> RunTags.grantTag(r, ct.tag());       // Diet Cola (on sell)
            case Effect.Destroy d -> { // self-destruct primitive (Mr Bones at BLIND_LOST; shared with Gros Michel/Pizza)
                if (d.selector() instanceof Selector.Self) r.pendingSelfDestruct.add(r.state.jokers().get(ctx.selfIndex));
                else throw new IllegalStateException("run-loop Destroy supports only Self: " + d);
            }
            default -> throw new IllegalStateException("not a run-loop effect: " + e);
        }
    }

    /** The Hook: discard {@code count} random held cards, then refill (honouring a Serpent draw override). */
    private static void hookDiscardAndRefill(Run r, int count) {
        for (int i = 0; i < count && !r.state.hand.isEmpty(); i++) {
            int idx = (int) (r.roll(RngSources.BOSS_HOOK) * r.state.hand.size());
            if (idx >= r.state.hand.size()) idx = r.state.hand.size() - 1;
            r.state.hand.remove(idx);
        }
        if (r.state.deck != null) {
            if (r.state.drawCountOverride > 0) r.state.deck.drawCount(r.state.hand, count);
            else r.state.deck.drawTo(r.state.hand, r.state.handSize);
        }
    }

    /** Observatory: resolve the held-Planet mult and which hand types you currently hold a Planet for,
     *  so the scorer can apply x1.5 to a played hand whose Planet you hold. */
    static void resolveObservatory(Run r) {
        r.state.heldPlanetMult = r.voucherFoldD(Value.Var.HELD_PLANET_MULT, 1.0);
        r.state.heldPlanetHands.clear();
        if (r.state.heldPlanetMult <= 1.0) return;
        for (String c : r.state.consumables) {
            PlanetCatalog.Planet p = PlanetCatalog.get(c);
            if (p != null) r.state.heldPlanetHands.add(p.hand());
        }
    }

    static void raiseSelfSellRules(Run r, Joker sold) {
        if (!(sold instanceof DataJoker dj)) return;
        EvaluationContext ctx = r.runLoopContext();
        ctx.jokers = List.of(sold); // self() = the sold joker, so its state (e.g. "rounds") is readable
        ctx.selfIndex = 0;
        for (Rule rule : dj.def().rules()) {
            if (rule.when() != Trigger.SELL_SELF) continue;
            if (rule.condition() != null && !ConditionEvaluator.test(rule.condition(), ctx)) continue;
            for (Effect e : rule.effects()) applyRunLoopEffect(r, e, ctx);
        }
    }

    /** Fire every owned data-joker's rules for a lifecycle {@code trigger} through the run-loop interpreter —
     *  the joker-side twin of {@link #raiseBossRules}. Used for capability effects (Perkeo on shop exit) that
     *  mutate run state, which the scoring/GameEvents paths don't apply. */
    static void raiseJokerRules(Run r, Trigger trigger) {
        r.pendingSelfDestruct.clear();
        EvaluationContext ctx = r.runLoopContext();
        ctx.jokers = r.state.jokers();
        List<Joker> owned = r.state.jokers();
        for (int i = 0; i < owned.size(); i++) {
            if (!(owned.get(i) instanceof DataJoker dj)) continue;
            ctx.selfIndex = i;
            for (Rule rule : dj.def().rules()) {
                if (rule.when() != trigger) continue;
                if (rule.condition() != null && !ConditionEvaluator.test(rule.condition(), ctx)) continue;
                for (Effect e : rule.effects()) applyRunLoopEffect(r, e, ctx);
            }
        }
        r.state.jokers().removeAll(r.pendingSelfDestruct); // consume jokers that did Destroy(Self) this pass
    }
}
