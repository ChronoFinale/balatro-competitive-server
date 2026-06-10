package com.balatromp.engine.scoring;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.consumable.Creation;
import com.balatromp.engine.hand.HandEvaluator;
import com.balatromp.engine.hand.HandMod;
import com.balatromp.engine.hand.HandMods;
import com.balatromp.engine.hand.HandResult;
import com.balatromp.engine.joker.EvaluationContext;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.JokerEffect;
import com.balatromp.engine.joker.Trigger;
import com.balatromp.engine.joker.def.DataJoker;
import com.balatromp.engine.rng.GameQueue;
import com.balatromp.engine.rng.QueueSet;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.state.RunState;
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
        log(acc, hr.type().display, "info", "Base " + acc.chips + " x " + acc.mult);

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
                applyCardScored(acc, card, run, queues, preview);
                for (int i = 0; i < jokers.size(); i++) {
                    ctx.phase = Trigger.ON_SCORED;
                    ctx.selfIndex = i;
                    ctx.blueprintDepth = 0;
                    ctx.scoredCard = card;
                    JokerEffect e = jokers.get(i).calculate(ctx);
                    apply(acc, e, jokers.get(i).name());
                    if (!preview) {
                        applyCardMods(acc, e, card);
                        applyCreate(e, run, queues);
                        if (destroysScored(e)) {
                            card.destroyed = true;
                            log(acc, card.toString(), "destroy", "Destroyed");
                        }
                        if (copiesScored(e)) {
                            Card copy = card.copy();
                            run.deckComposition.add(copy);
                            run.hand.add(copy);
                            log(acc, card.toString(), "copy", "Copied to deck");
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
                applyCardHeld(acc, card);
                for (int i = 0; i < jokers.size(); i++) {
                    ctx.phase = Trigger.ON_HELD;
                    ctx.selfIndex = i;
                    ctx.blueprintDepth = 0;
                    ctx.scoredCard = card;
                    JokerEffect e = jokers.get(i).calculate(ctx);
                    apply(acc, e, jokers.get(i).name());
                    if (!preview) applyCardMods(acc, e, card);
                }
            }
        }

        // (6) main joker pass + joker-on-joker.
        for (int i = 0; i < jokers.size(); i++) {
            Joker current = jokers.get(i);
            ctx.phase = Trigger.JOKER_MAIN;
            ctx.selfIndex = i;
            ctx.blueprintDepth = 0;
            ctx.scoredCard = null;
            ctx.otherJoker = null;
            JokerEffect me = current.calculate(ctx);
            apply(acc, me, current.name());
            if (!preview) {
                applyCreate(me, run, queues);
                applyLevelUp(me, run);
            }

            for (int j = 0; j < jokers.size(); j++) {
                ctx.phase = Trigger.ON_OTHER_JOKER;
                ctx.selfIndex = j;
                ctx.blueprintDepth = 0;
                ctx.otherJoker = current;
                apply(acc, jokers.get(j).calculate(ctx), jokers.get(j).name());
            }
        }

        // (6b) FINAL_SCORING_STEP — effect seam after the main joker pass.
        effectPass(Trigger.FINAL_SCORING_STEP, acc, ctx, jokers);

        // (6c) credit money earned during scoring (Rough Gem, Business Card, Bootstraps,
        // Gold/Lucky seals already credited inline). Never in preview — it's a dry-run.
        if (!preview && acc.dollars != 0) {
            run.money = Math.max(0, run.money + acc.dollars);
        }

        // (7) final score (big-number; chips × mult).
        BigNum score = acc.chips.multiply(acc.mult);
        return new ScoreResult(hr.type(), Math.round(acc.chips.doubleValue()), acc.mult.doubleValue(),
                score.doubleValue(), acc.log, acc.destroyed, score);
    }

    /** Apply any card mutations carried by an effect (and its extra-chain) to the scored card. */
    private void applyCardMods(Acc acc, JokerEffect e, Card target) {
        if (target == null) return;
        for (JokerEffect cur = e; cur != null; cur = cur.extra) {
            if (cur.cardMod != null) {
                cur.cardMod.applyTo(target);
                log(acc, target.toString(), "mutate", cur.cardMod.action().name());
            }
        }
    }

    /** Apply any CREATE effects in the chain (real play only — never previewed). */
    private void applyCreate(JokerEffect e, RunState run, QueueSet queues) {
        for (JokerEffect cur = e; cur != null; cur = cur.extra) {
            if (cur.create != null) Creation.apply(run, cur.create, queues);
        }
    }

    /** Apply any LEVEL_UP_HAND effects in the chain (real play only). */
    private void applyLevelUp(JokerEffect e, RunState run) {
        for (JokerEffect cur = e; cur != null; cur = cur.extra) {
            if (cur.levelUpHand != null) {
                for (int i = 0; i < cur.levelUpAmount; i++) run.levelUpHand(cur.levelUpHand);
            }
        }
    }

    /** Mark the scored card destroyed if any effect in the chain says so (real play only). */
    private static boolean destroysScored(JokerEffect e) {
        for (JokerEffect cur = e; cur != null; cur = cur.extra) {
            if (cur.destroyScored) return true;
        }
        return false;
    }

    private static boolean copiesScored(JokerEffect e) {
        for (JokerEffect cur = e; cur != null; cur = cur.extra) {
            if (cur.copyScored) return true;
        }
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
        if (card.seal == Seal.RED) {
            reps += 1;
            log(acc, "Red Seal", "retrigger", "Retrigger");
        }
        for (int i = 0; i < jokers.size(); i++) {
            ctx.phase = phase;
            ctx.selfIndex = i;
            ctx.blueprintDepth = 0;
            ctx.scoredCard = card;
            JokerEffect e = jokers.get(i).calculate(ctx);
            if (e != null && e.repetitions > 0) {
                reps += e.repetitions;
                log(acc, jokers.get(i).name(), "retrigger", "Retrigger x" + e.repetitions);
            }
        }
        return reps;
    }

    /** Card base + enhancement + edition + seal, applied as one ordered entry. */
    private void applyCardScored(Acc acc, Card card, RunState run, QueueSet queues, boolean preview) {
        long chips = card.baseChips();
        if (card.enhancement == Enhancement.STONE) chips += 50;
        if (card.enhancement == Enhancement.BONUS) chips += 30;
        chips += card.permaChips; // permanent per-card chip bonus (Hiker etc.)
        if (chips != 0) {
            acc.chips = acc.chips.add(BigNum.of(chips));
            log(acc, card.toString(), "chips", "+" + chips + " Chips");
        }
        if (card.permaMult != 0) {
            acc.mult = acc.mult.add(BigNum.of(card.permaMult)); // permanent per-card mult bonus
            log(acc, card.toString(), "mult", "+" + fmt(card.permaMult) + " Mult");
        }
        if (card.enhancement == Enhancement.MULT) {
            acc.mult = acc.mult.add(BigNum.of(4));
            log(acc, card.toString(), "mult", "+4 Mult");
        }
        if (card.enhancement == Enhancement.LUCKY) {
            // Two independent game-long hit/miss queues: 1-in-5 for +20 Mult,
            // 1-in-15 for +$20. Each Lucky trigger pops the next roll; the
            // threshold honors probabilityNumerator (Oops! All 6s).
            if (lucky(queues, "lucky_mult") < run.probabilityNumerator / 5.0) {
                acc.mult = acc.mult.add(BigNum.of(20));
                log(acc, card.toString(), "mult", "Lucky! +20 Mult");
                if (!preview) run.luckyTriggersTotal++; // Lucky Cat counts triggers
            }
            if (lucky(queues, "lucky_money") < run.probabilityNumerator / 15.0) {
                if (!preview) {
                    run.money += 20;
                    run.luckyTriggersTotal++;
                }
                log(acc, card.toString(), "dollars", "Lucky! +$20");
            }
        }
        if (card.enhancement == Enhancement.GLASS) {
            double glassMult = run.multiplayer ? 1.5 : 2.0; // multiplayer nerfs Glass to x1.5
            acc.mult = acc.mult.multiply(glassMult);
            log(acc, card.toString(), "xmult", "x" + fmt(glassMult) + " Mult");
            if (!preview && lucky(queues, "glass") < 0.25) { // game-long break queue (1-in-4)
                card.destroyed = true;
                acc.destroyed.add(card);
            }
        }
        applyCardEdition(acc, card);
        if (card.seal == Seal.GOLD) {
            if (!preview) run.money += 3;
            log(acc, card.toString(), "dollars", "+$3 (Gold Seal)");
        }
    }

    private void applyCardEdition(Acc acc, Card card) {
        switch (card.edition) {
            case FOIL -> { acc.chips = acc.chips.add(BigNum.of(50)); log(acc, card.toString(), "chips", "+50 Chips (Foil)"); }
            case HOLOGRAPHIC -> { acc.mult = acc.mult.add(BigNum.of(10)); log(acc, card.toString(), "mult", "+10 Mult (Holo)"); }
            case POLYCHROME -> { acc.mult = acc.mult.multiply(1.5); log(acc, card.toString(), "xmult", "x1.5 Mult (Poly)"); }
            default -> { }
        }
    }

    /** Held-in-hand card effects (Steel x1.5 mult while held). */
    private void applyCardHeld(Acc acc, Card card) {
        if (card.enhancement == Enhancement.STEEL) {
            acc.mult = acc.mult.multiply(1.5);
            log(acc, card.toString(), "xmult", "x1.5 Mult (Steel)");
        }
    }

    /** Entry point: resolve the source label, then apply per SMODS calculation order. */
    private void apply(Acc acc, JokerEffect e, String defaultSource) {
        if (e == null) return;
        applyEffect(acc, e, e.source != null ? e.source : defaultSource);
    }

    /**
     * Apply one effect's fields in the real per-source order (doc 30 §1b,
     * {@code calculation_keys}): {@code chips → mult → xchips → xmult}, then recurse
     * the {@code extra} chain, then non-scoring keys (dollars / swap / balance /
     * message). {@code x}-fields are guarded by {@code amount != 1}. {@code hMult}
     * (held +mult) is additive and joins the mult step.
     */
    private void applyEffect(Acc acc, JokerEffect e, String src) {
        if (e == null) return;
        if (e.chips != 0) {
            acc.chips = acc.chips.add(BigNum.of(e.chips));
            log(acc, src, "chips", e.message != null ? e.message : "+" + e.chips + " Chips");
        }
        double addMult = e.mult + e.hMult;
        if (addMult != 0) {
            acc.mult = acc.mult.add(BigNum.of(addMult));
            log(acc, src, "mult", e.message != null ? e.message : "+" + fmt(addMult) + " Mult");
        }
        if (e.xChips != null && e.xChips != 1.0) {
            acc.chips = acc.chips.multiply(e.xChips);
            log(acc, src, "xchips", "x" + fmt(e.xChips) + " Chips");
        }
        if (e.xMult != null && e.xMult != 1.0) {
            acc.mult = acc.mult.multiply(e.xMult);
            log(acc, src, "xmult", e.message != null ? e.message : "x" + fmt(e.xMult) + " Mult");
        }
        if (e.powMult != null && e.powMult != 1.0) {
            acc.mult = acc.mult.pow(e.powMult); // exponential (Cryptid e_mult-style) — needs BigNum
            log(acc, src, "powmult", "^" + fmt(e.powMult) + " Mult");
        }
        applyEffect(acc, e.extra, src); // the extra-chain recurses with the same ordering
        if (e.dollars != 0) {
            acc.dollars += e.dollars;
            acc.log.add(new ReplayEntry(src, "dollars", (e.dollars >= 0 ? "+$" : "-$") + Math.abs(e.dollars),
                    Math.round(acc.chips.doubleValue()), acc.mult.doubleValue()));
        }
        if (e.swap) {
            BigNum c = acc.chips;
            acc.chips = acc.mult;
            acc.mult = c;
            log(acc, src, "swap", "Swap Chips & Mult");
        }
        // e.balance: deferred — semantics unsettled (doc 30 open Q) and no content uses it yet.
    }

    /** Pop the next roll [0,1) from a named game-long probability queue. */
    private static double lucky(QueueSet queues, String key) {
        GameQueue<Double> q = queues.queue(key, com.balatromp.engine.rng.Rng::nextDouble);
        return q.next();
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

    private void log(Acc acc, String source, String kind, String text) {
        acc.log.add(new ReplayEntry(source, kind, text,
                Math.round(acc.chips.doubleValue()), acc.mult.doubleValue()));
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
