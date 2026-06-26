package com.balatro.engine.scoring;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import com.balatro.engine.consumable.Creation;
import com.balatro.engine.hand.HandEvaluator;
import com.balatro.engine.hand.HandMod;
import com.balatro.engine.hand.HandMods;
import com.balatro.engine.hand.HandResult;
import com.balatro.engine.exec.Command;
import com.balatro.engine.joker.Contribution;
import com.balatro.engine.joker.EvaluationContext;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerResult;
import com.balatro.grammar.Effect;
import com.balatro.grammar.Trigger;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.rng.RngSources;
import com.balatro.engine.state.RunState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The authoritative scoring pipeline — a faithful transcription of Balatro's
 * {@code G.FUNCS.evaluate_play} (spec §1). Runs only on the server. Given the
 * played cards, the cards still held in hand, the joker row, and run state, it
 * computes chips × mult deterministically and produces a replay log.
 *
 * Pipeline order (spec §1):
 *   1. detect hand, assemble scoring set (+ Stone cards)
 *   2. base chips/mult from hand level
 *   3. BEFORE pass (jokers)
 *   4. each scored card, left-to-right, with retriggers; apply
 *      base+enhancement+edition, then per-card joker effects (chips→mult→xMult)
 *   5. each held-in-hand card (Steel, etc.) with retriggers
 *   6. main joker pass, then joker-on-joker
 *   7. score = chips × mult
 */
public final class ScoringEngine {

    /** Mutable accumulator for one evaluation. Chips/mult are {@link BigNum} so
     *  scoring scales past double (Cryptid/high-ante) while staying double-exact in range. */
    private static final class Acc {
        BigNum chips = BigNum.ZERO;
        BigNum mult = BigNum.ZERO;
        int dollars = 0; // money earned during scoring (credited once at the end, never in preview)
        final List<ReplayEntry> log = new ArrayList<>();
        final List<Card> destroyed = new ArrayList<>();
    }

    public ScoreResult score(List<Card> played, List<Card> held, RunState run, RandomStreams rng) {
        return score(played, held, run, rng, false);
    }

    /**
     * A side-effect-free projection of the score for a given card selection — what
     * the client shows as a live preview as the player selects cards. Computes the
     * full pipeline (so per-joker contributions appear in the replay log) but
     * commits nothing: no money credited, no cards destroyed or mutated, and the
     * run's real RNG queues are never advanced (a throwaway queue is used).
     */
    public ScoreResult preview(List<Card> played, List<Card> held, RunState run, RandomStreams rng) {
        return score(played, held, run, rng, true);
    }

    /*
     * ── ORDERING CONTRACT (the determinism invariant; do not change without updating the guard tests) ──
     * 1. PHASES run in {@link Trigger} order: MODIFY_SCORING_HAND → BEFORE → INITIAL_SCORING_STEP →
     *    (per scored card: ON_SCORED + its REPETITION_PLAYED retriggers) → (per held card: ON_HELD +
     *    REPETITION_HELD) → JOKER_MAIN → ON_OTHER_JOKER → FINAL_SCORING_STEP → AFTER → destruction.
     * 2. WITHIN a phase, jokers apply in JOKER-AREA ORDER (left→right, the {@code run.jokers()} index). This
     *    is POSITIONAL, not arithmetic: a +Mult joker LEFT of a ×Mult joker is inside the multiply; to its
     *    RIGHT it is not. Guard: {@code JokerOrderTest.effectsApplyInJokerOrderNotAddThenMultiply}.
     * 3. WITHIN a joker, rules apply in AUTHORING ORDER, each fully (incl. its state write) before the next
     *    rule's condition is tested — so a counter bump is visible to a later rule. Guard: RuleAccumulationTest.
     * 4. A RETRIGGER (REPETITION_*) re-runs that card's ON_SCORED/ON_HELD pass in place, N extra times.
     * 5. Copiers (Blueprint→right neighbour, Brainstorm→leftmost) re-enter the target at the SAME phase,
     *    bumping {@code blueprintDepth} (the recursion guard). Guard: DataJokerTest blueprint cases.
     * Each RNG draw comes off a keyed game-long queue, so order-independence of variance is by construction.
     */
    private ScoreResult score(List<Card> played, List<Card> held, RunState run, RandomStreams rng,
                              boolean preview) {
        HandMods mods = activeMods(run.jokers());
        HandResult hr = HandEvaluator.evaluate(played, mods);

        // (1) scoring set = detected cards + Stone cards (+ ALL played under Splash), in play order.
        List<Card> scoring = new ArrayList<>(hr.scoringCards());
        for (Card c : played) {
            if ((c.isStone() || mods.splash()) && !containsIdentity(scoring, c)) scoring.add(c);
        }
        scoring.sort((a, b) -> Integer.compare(played.indexOf(a), played.indexOf(b)));

        // (2) base chips/mult from the hand's current level.
        Acc acc = new Acc();
        acc.chips = BigNum.of(hr.type().baseChips + run.handLevelChipBonus(hr.type()));
        acc.mult = BigNum.of(hr.type().baseMult + run.handLevelMultBonus(hr.type()));
        if (run.bossHalveBase) { // The Flint: halve the base chips and mult before any joker
            acc.chips = acc.chips.multiply(0.5);
            acc.mult = acc.mult.multiply(0.5);
        }
        log(acc, hr.type().name(), ReplayEntry.Kind.INFO, 0); // hand-type key (e.g. HIGH_CARD), not prose

        List<Joker> jokers = run.jokers();
        EvaluationContext ctx = baseContext(hr, played, scoring, held, run, rng, jokers);
        ctx.allFaces = mods.pareidolia(); // Pareidolia: face conditions treat every card as a face

        // Probabilistic card effects (Lucky, Glass) read game-long hit/miss queues
        // (BMP shape): both players get the same procs for the same cards played,
        // regardless of order/timing. Fall back to a transient set for bare-state
        // tests that pass an rng directly without a persistent run.
        QueueSet queues = preview ? new QueueSet(new RandomStreams("preview"))
                : (run.queues != null) ? run.queues : new QueueSet(rng);
        ctx.preview = preview;

        // (3) BEFORE pass.
        for (int i = 0; i < jokers.size(); i++) {
            if (perished(run, jokers.get(i))) continue; // Perishable sticker expired -> joker disabled
            ctx.phase = Trigger.BEFORE;
            ctx.selfIndex = i;
            ctx.blueprintDepth = 0;
            ctx.scoredCard = null;
            apply(acc, jokers.get(i).calculate(ctx), jokers.get(i).name());
        }

        // (3b) INITIAL_SCORING_STEP — effect seam after base, before per-card scoring.
        effectPass(Trigger.INITIAL_SCORING_STEP, acc, ctx, jokers);

        // (4) score each played card, left-to-right.
        for (Card card : scoring) {
            if (card.debuffed) continue;
            int reps = retriggers(card, Trigger.REPETITION_PLAYED, ctx, jokers, acc);
            for (int r = 0; r < reps; r++) {
                applyCardScored(acc, card, run, queues, preview, ctx);
                for (int i = 0; i < jokers.size(); i++) {
                    if (perished(run, jokers.get(i))) continue;
                    ctx.phase = Trigger.ON_SCORED;
                    ctx.selfIndex = i;
                    ctx.blueprintDepth = 0;
                    ctx.scoredCard = card;
                    JokerResult e = jokers.get(i).calculate(ctx);
                    apply(acc, e, jokers.get(i).name());
                    if (!preview) {
                        applyCardMods(acc, e, card);
                        applyCreate(e, run, queues);
                        if (destroysScored(e)) {
                            card.destroyed = true;
                            log(acc, card.toString(), ReplayEntry.Kind.DESTROY, 0);
                        }
                        if (copiesScored(e)) {
                            Card copy = card.copy();
                            run.deckComposition.add(copy);
                            run.hand.add(copy);
                            log(acc, card.toString(), ReplayEntry.Kind.COPY, 0);
                        }
                    }
                }
            }
        }

        // (5) held-in-hand cards.
        for (Card card : held) {
            if (card.debuffed) continue;
            ctx.scoredCard = card;
            int reps = retriggers(card, Trigger.REPETITION_HELD, ctx, jokers, acc);
            for (int r = 0; r < reps; r++) {
                applyCardHeld(acc, card, ctx);
                for (int i = 0; i < jokers.size(); i++) {
                    ctx.phase = Trigger.ON_HELD;
                    ctx.selfIndex = i;
                    ctx.blueprintDepth = 0;
                    ctx.scoredCard = card;
                    JokerResult e = jokers.get(i).calculate(ctx);
                    apply(acc, e, jokers.get(i).name());
                    if (!preview) applyCardMods(acc, e, card);
                }
            }
        }

        // (6) main joker pass + joker-on-joker.
        for (int i = 0; i < jokers.size(); i++) {
            Joker current = jokers.get(i);
            if (perished(run, current)) continue; // disabled by an expired Perishable sticker
            ctx.phase = Trigger.JOKER_MAIN;
            ctx.selfIndex = i;
            ctx.blueprintDepth = 0;
            ctx.scoredCard = null;
            ctx.otherJoker = null;
            // Joker editions (Foil/Holo add before the joker scores; Poly multiplies after).
            Edition ed = run.jokerEdition(current);
            if (ed == Edition.FOIL) { acc.chips = acc.chips.add(BigNum.of(50)); log(acc, current.name(), ReplayEntry.Kind.CHIPS, 50); }
            else if (ed == Edition.HOLOGRAPHIC) { acc.mult = acc.mult.add(BigNum.of(10)); log(acc, current.name(), ReplayEntry.Kind.MULT, 10); }
            JokerResult me = current.calculate(ctx);
            apply(acc, me, current.name());
            if (ed == Edition.POLYCHROME) { acc.mult = acc.mult.multiply(1.5); log(acc, current.name(), ReplayEntry.Kind.XMULT, 1.5); }
            if (!preview) {
                applyCreate(me, run, queues);
                applyLevelUp(me, run);
            }

            for (int j = 0; j < jokers.size(); j++) {
                if (perished(run, jokers.get(j))) continue;
                ctx.phase = Trigger.ON_OTHER_JOKER;
                ctx.selfIndex = j;
                ctx.blueprintDepth = 0;
                ctx.otherJoker = current;
                apply(acc, jokers.get(j).calculate(ctx), jokers.get(j).name());
            }
        }

        // (6b) FINAL_SCORING_STEP — effect seam after the main joker pass.
        effectPass(Trigger.FINAL_SCORING_STEP, acc, ctx, jokers);

        // Observatory voucher: a held Planet gives xMult to its own hand (resolved on RunState by Run).
        if (run.heldPlanetMult > 1.0 && run.heldPlanetHands.contains(hr.type())) {
            acc.mult = acc.mult.multiply(run.heldPlanetMult);
            log(acc, "v_observatory", ReplayEntry.Kind.XMULT, run.heldPlanetMult);
        }

        // (6c) credit money earned during scoring (Rough Gem, Business Card, Bootstraps,
        // Gold/Lucky seals already credited inline). Never in preview — it's a dry-run.
        if (!preview && acc.dollars != 0) {
            run.money = Math.max(0, run.money + acc.dollars);
        }

        // Plasma Deck: balance chips and mult — each becomes floor((chips+mult)/2) — before the multiply.
        if (run.balanceChipsMult) {
            BigNum half = acc.chips.add(acc.mult).multiply(0.5).floor();
            acc.chips = half;
            acc.mult = half;
            log(acc, "b_plasma", ReplayEntry.Kind.INFO, 0);
        }

        // (7) final score (big-number; chips × mult).
        BigNum score = acc.chips.multiply(acc.mult);
        return new ScoreResult(hr.type(), Math.round(acc.chips.doubleValue()), acc.mult.doubleValue(),
                score.doubleValue(), acc.log, acc.destroyed, score);
    }

    /** A disabled joker contributes nothing to scoring — either its Perishable sticker has expired
     *  ("debuffed") or a boss has switched it off this hand ("bossDisabled", e.g. Crimson Heart). */
    private static boolean perished(RunState run, Joker j) {
        return run.jokerFlag(j, "debuffed") || run.jokerFlag(j, "bossDisabled");
    }

    /** Apply any card mutations carried by the result's commands to the scored card (MutateScoredCard). */
    private void applyCardMods(Acc acc, JokerResult e, Card target) {
        if (target == null) return;
        for (Command cmd : e.commands()) {
            if (cmd instanceof Command.MutateScoredCard m) {
                m.mod().applyTo(target);
                log(acc, target.toString(), ReplayEntry.Kind.MUTATE, 0);
            }
        }
    }

    /** Apply any CREATE commands (real play only — never previewed). */
    private void applyCreate(JokerResult e, RunState run, QueueSet queues) {
        for (Command cmd : e.commands()) {
            if (cmd instanceof Command.Create cr) Creation.apply(run, cr.spec(), queues);
        }
    }

    /** Apply any LevelHand commands to the played hand (real play only). */
    private void applyLevelUp(JokerResult e, RunState run) {
        for (Command cmd : e.commands()) {
            if (cmd instanceof Command.LevelHand lh) {
                for (int i = 0; i < lh.levels(); i++) run.levelUpHand(lh.hand());
            }
        }
    }

    /** Mark the scored card destroyed if any command says so (real play only). */
    private static boolean destroysScored(JokerResult e) {
        for (Command cmd : e.commands()) if (cmd instanceof Command.DestroyScored) return true;
        return false;
    }

    private static boolean copiesScored(JokerResult e) {
        for (Command cmd : e.commands()) if (cmd instanceof Command.CopyScored) return true;
        return false;
    }

    /** An effect-producing joker pass for a whole-hand phase (BEFORE-style seams). */
    private void effectPass(Trigger phase, Acc acc, EvaluationContext ctx, List<Joker> jokers) {
        for (int i = 0; i < jokers.size(); i++) {
            ctx.phase = phase;
            ctx.selfIndex = i;
            ctx.blueprintDepth = 0;
            ctx.scoredCard = null;
            ctx.otherJoker = null;
            apply(acc, jokers.get(i).calculate(ctx), jokers.get(i).name());
        }
    }

    private EvaluationContext baseContext(HandResult hr, List<Card> played, List<Card> scoring,
                                          List<Card> held, RunState run, RandomStreams rng,
                                          List<Joker> jokers) {
        EvaluationContext ctx = new EvaluationContext();
        ctx.handType = hr.type();
        ctx.playedCards = played;
        ctx.scoringCards = scoring;
        ctx.heldCards = held;
        ctx.jokers = jokers;
        ctx.run = run;
        ctx.rng = rng;
        return ctx;
    }

    /** reps = 1 + red seal + sum of joker repetition effects. */
    private int retriggers(Card card, Trigger phase, EvaluationContext ctx,
                           List<Joker> jokers, Acc acc) {
        int reps = 1;
        // Seal retriggers are DATA: Red Seal = Score(ADD, RETRIGGERS, 1) in CardModifiers.SEAL, resolved here
        // in the retrigger pass (the scoring pass ignores RETRIGGERS, so the same effect is a no-op there).
        for (com.balatro.grammar.Effect e
                : com.balatro.content.CardModifiers.SEAL.getOrDefault(card.seal, java.util.List.of())) {
            if (e instanceof com.balatro.grammar.Effect.Score s
                    && s.term() == com.balatro.grammar.Effect.Term.RETRIGGERS) {
                int r = retriggerAmount(com.balatro.engine.eval.EffectInterpreter.apply(s, ctx));
                if (r > 0) {
                    reps += r;
                    log(acc, "seal_red", ReplayEntry.Kind.RETRIGGER, 1);
                }
            }
        }
        for (int i = 0; i < jokers.size(); i++) {
            ctx.phase = phase;
            ctx.selfIndex = i;
            ctx.blueprintDepth = 0;
            ctx.scoredCard = card;
            int r = retriggerAmount(jokers.get(i).calculate(ctx));
            if (r > 0) {
                reps += r;
                log(acc, jokers.get(i).name(), ReplayEntry.Kind.RETRIGGER, r);
            }
        }
        return reps;
    }

    /** Sum the (ADD, RETRIGGERS) contributions in a result — the count-up pass ignores them; only this
     *  REPETITION pass reads them. */
    private static int retriggerAmount(JokerResult r) {
        int total = 0;
        for (Contribution c : r.contributions()) {
            if (c.term() == Effect.Term.RETRIGGERS) total += (int) c.amount();
        }
        return total;
    }

    /** Card base + enhancement + edition + seal, applied as one ordered entry. */
    private void applyCardScored(Acc acc, Card card, RunState run, QueueSet queues, boolean preview,
            com.balatro.engine.joker.EvaluationContext ctx) {
        long chips = card.baseChips() + card.permaChips; // card face value + permanent bonus (Hiker etc.)
        if (chips != 0) {
            acc.chips = acc.chips.add(BigNum.of(chips));
            log(acc, card.toString(), ReplayEntry.Kind.CHIPS, chips);
        }
        if (card.permaMult != 0) {
            acc.mult = acc.mult.add(BigNum.of(card.permaMult)); // permanent per-card mult bonus
            log(acc, card.toString(), ReplayEntry.Kind.MULT, card.permaMult);
        }
        // Enhancement scoring is DATA now: Bonus/Mult/Stone are Effect.Score, interpreted like a joker
        // (the structural "Stone always scores / no rank-suit" stays in the scoring-set selection above).
        applyCardModifierEffects(acc, com.balatro.content.CardModifiers.ENHANCEMENT.get(card.enhancement), ctx, card);
        // Lucky is DATA now: chance(1/5 on lucky_mult) -> +20 Mult, chance(1/15 on lucky_money) -> +$20,
        // rolled on the same dedicated streams (so determinism is byte-identical). Chance floors in preview.
        applyCardModifierRules(acc, com.balatro.content.CardModifiers.PROBABILISTIC.get(card.enhancement), ctx, card, run);
        // Glass x-mult is DATA (ENHANCEMENT table, reads GLASS_MULT). Only the 1-in-4 shatter — a structural
        // card destruction on its own RNG queue — stays here.
        if (card.enhancement == Enhancement.GLASS && !preview
                && queues.roll(RngSources.GLASS, run.rngContext()) < 0.25) {
            card.destroyed = true;
            acc.destroyed.add(card);
        }
        // Edition scoring is DATA: Foil +50 chips, Holo +10 mult, Poly x1.5 mult — same Effect.Score path.
        applyCardModifierEffects(acc, com.balatro.content.CardModifiers.EDITION.get(card.edition), ctx, card);
        // Seal scoring is DATA: Gold = +$3 (Effect.dollars, credited at end). Red/Blue/Purple are elsewhere.
        applyCardModifierEffects(acc, com.balatro.content.CardModifiers.SEAL.get(card.seal), ctx, card);
    }

    /** Apply a card modifier's scoring effects (enhancement/edition) through the joker interpreter. */
    private void applyCardModifierEffects(Acc acc, java.util.List<com.balatro.grammar.Effect> effects,
            com.balatro.engine.joker.EvaluationContext ctx, Card card) {
        if (effects == null) return;
        for (com.balatro.grammar.Effect e : effects) {
            if (e instanceof com.balatro.grammar.Effect.Score s) apply(acc, com.balatro.engine.eval.EffectInterpreter.apply(s, ctx), card.toString());
        }
    }

    /** Apply a card modifier's PROBABILISTIC rules (Lucky): each rule whose {@link Condition} procs on its
     *  dedicated stream contributes its score/dollars effects. The chance floors to false in preview. */
    private void applyCardModifierRules(Acc acc, java.util.List<com.balatro.grammar.Rule> rules,
            com.balatro.engine.joker.EvaluationContext ctx, Card card, RunState run) {
        if (rules == null) return;
        for (com.balatro.grammar.Rule r : rules) {
            if (!com.balatro.engine.eval.ConditionEvaluator.test(r.condition(), ctx)) continue;
            applyCardModifierEffects(acc, r.effects(), ctx, card);
            if (card.enhancement == Enhancement.LUCKY) run.luckyTriggersTotal++; // Lucky Cat counts each proc
        }
    }

    /** Held-in-hand card effects (Steel x1.5 mult while held) — data, via the joker interpreter. */
    private void applyCardHeld(Acc acc, Card card, com.balatro.engine.joker.EvaluationContext ctx) {
        applyCardModifierEffects(acc, com.balatro.content.CardModifiers.HELD.get(card.enhancement), ctx, card);
    }

    /**
     * Fold a joker's scoring {@link Contribution}s into the accumulator. Each contribution is one (op, term)
     * cell applied in LIST ORDER — positional, not canonical: a {@code +mult} contributed after an {@code xMult}
     * lands OUTSIDE the multiply, exactly as the old per-link {@code extra}-chain did. {@code DOLLARS} is the one
     * exception: the old {@code applyEffect} logged dollars only AFTER recursing the {@code extra}-subtree, so a
     * joker mixing {@code $} with scoring emitted its DOLLARS entry last (multiple in reverse link order). That
     * byte-exact ordering is preserved here: non-dollars in list order, then dollars in reverse. RETRIGGERS are
     * read only by {@link #retriggers}, never here. Source falls back to the joker (or the copier's relabel).
     */
    private void apply(Acc acc, JokerResult r, String defaultSource) {
        if (r == null) return;
        List<Contribution> cs = r.contributions();
        for (Contribution c : cs) {
            if (c.term() == Effect.Term.DOLLARS || c.term() == Effect.Term.RETRIGGERS) continue;
            applyContribution(acc, c, c.source() != null ? c.source() : defaultSource);
        }
        for (int i = cs.size() - 1; i >= 0; i--) { // dollars deferred to the end, in reverse (extra-chain unwind)
            Contribution c = cs.get(i);
            if (c.term() != Effect.Term.DOLLARS) continue;
            String src = c.source() != null ? c.source() : defaultSource;
            acc.dollars += (long) c.amount();
            acc.log.add(new ReplayEntry(src, ReplayEntry.Kind.DOLLARS, c.amount(),
                    Math.round(acc.chips.doubleValue()), acc.mult.doubleValue()));
        }
    }

    /** Apply one scoring contribution: ADD chips/mult(+held), MULTIPLY chips/mult, POWER mult. Zero/identity
     *  cells were already dropped by the interpreter, so every contribution here is live. */
    private void applyContribution(Acc acc, Contribution c, String src) {
        switch (c.op()) {
            case ADD -> {
                switch (c.term()) {
                    case CHIPS -> { acc.chips = acc.chips.add(BigNum.of((long) c.amount())); log(acc, src, ReplayEntry.Kind.CHIPS, c.amount()); }
                    case MULT, HELD_MULT -> { acc.mult = acc.mult.add(BigNum.of(c.amount())); log(acc, src, ReplayEntry.Kind.MULT, c.amount()); }
                    default -> { /* DOLLARS / RETRIGGERS handled outside the fold */ }
                }
            }
            case MULTIPLY -> {
                switch (c.term()) {
                    case CHIPS -> { acc.chips = acc.chips.multiply(c.amount()); log(acc, src, ReplayEntry.Kind.XCHIPS, c.amount()); }
                    case MULT -> { acc.mult = acc.mult.multiply(c.amount()); log(acc, src, ReplayEntry.Kind.XMULT, c.amount()); }
                    default -> throw new IllegalStateException("MULTIPLY unsupported for term " + c.term());
                }
            }
            case POWER -> { acc.mult = acc.mult.pow(c.amount()); log(acc, src, ReplayEntry.Kind.POWMULT, c.amount()); } // Cryptid e_mult-style
            default -> throw new IllegalStateException(c.op() + " is not a scoring operation");
        }
    }

    /** Union the global hand modifiers granted by the player's data-driven jokers. */
    private static HandMods activeMods(List<Joker> jokers) {
        EnumSet<HandMod> mods = EnumSet.noneOf(HandMod.class);
        for (Joker j : jokers) {
            if (j instanceof DataJoker dj) mods.addAll(dj.def().handMods());
        }
        return HandMods.from(mods);
    }

    private static boolean containsIdentity(List<Card> list, Card c) {
        for (Card x : list) if (x == c) return true;
        return false;
    }

    private void log(Acc acc, String source, ReplayEntry.Kind kind, double amount) {
        acc.log.add(new ReplayEntry(source, kind, amount,
                Math.round(acc.chips.doubleValue()), acc.mult.doubleValue()));
    }
}
