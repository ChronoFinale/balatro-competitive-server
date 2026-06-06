package com.balatromp.engine.scoring;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.hand.HandEvaluator;
import com.balatromp.engine.hand.HandResult;
import com.balatromp.engine.joker.EvaluationContext;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.JokerEffect;
import com.balatromp.engine.joker.Trigger;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.state.RunState;
import java.util.ArrayList;
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

    /** Mutable accumulator for one evaluation. */
    private static final class Acc {
        long chips;
        double mult;
        final List<ReplayEntry> log = new ArrayList<>();
        final List<Card> destroyed = new ArrayList<>();
    }

    public ScoreResult score(List<Card> played, List<Card> held, RunState run, RandomStreams rng) {
        HandResult hr = HandEvaluator.evaluate(played);

        // (1) scoring set = detected cards + any Stone cards, in play order.
        List<Card> scoring = new ArrayList<>(hr.scoringCards());
        for (Card c : played) {
            if (c.isStone() && !containsIdentity(scoring, c)) scoring.add(c);
        }
        scoring.sort((a, b) -> Integer.compare(played.indexOf(a), played.indexOf(b)));

        // (2) base chips/mult from the hand's current level.
        Acc acc = new Acc();
        acc.chips = hr.type().baseChips + run.handLevelChipBonus(hr.type());
        acc.mult = hr.type().baseMult + run.handLevelMultBonus(hr.type());
        log(acc, hr.type().display, "info", "Base " + acc.chips + " x " + fmt(acc.mult));

        List<Joker> jokers = run.jokers();
        EvaluationContext ctx = baseContext(hr, played, scoring, run, rng, jokers);

        // (3) BEFORE pass.
        for (int i = 0; i < jokers.size(); i++) {
            ctx.phase = Trigger.BEFORE;
            ctx.selfIndex = i;
            ctx.blueprintDepth = 0;
            ctx.scoredCard = null;
            apply(acc, jokers.get(i).calculate(ctx), jokers.get(i).name());
        }

        // (4) score each played card, left-to-right.
        for (Card card : scoring) {
            if (card.debuffed) continue;
            int reps = retriggers(card, Trigger.REPETITION_PLAYED, ctx, jokers, acc);
            for (int r = 0; r < reps; r++) {
                applyCardScored(acc, card, run, rng);
                for (int i = 0; i < jokers.size(); i++) {
                    ctx.phase = Trigger.ON_SCORED;
                    ctx.selfIndex = i;
                    ctx.blueprintDepth = 0;
                    ctx.scoredCard = card;
                    apply(acc, jokers.get(i).calculate(ctx), jokers.get(i).name());
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
                    apply(acc, jokers.get(i).calculate(ctx), jokers.get(i).name());
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
            apply(acc, current.calculate(ctx), current.name());

            for (int j = 0; j < jokers.size(); j++) {
                ctx.phase = Trigger.ON_OTHER_JOKER;
                ctx.selfIndex = j;
                ctx.blueprintDepth = 0;
                ctx.otherJoker = current;
                apply(acc, jokers.get(j).calculate(ctx), jokers.get(j).name());
            }
        }

        // (7) final score.
        double score = acc.chips * acc.mult;
        return new ScoreResult(hr.type(), acc.chips, acc.mult, score, acc.log, acc.destroyed);
    }

    private EvaluationContext baseContext(HandResult hr, List<Card> played, List<Card> scoring,
                                          RunState run, RandomStreams rng, List<Joker> jokers) {
        EvaluationContext ctx = new EvaluationContext();
        ctx.handType = hr.type();
        ctx.playedCards = played;
        ctx.scoringCards = scoring;
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
    private void applyCardScored(Acc acc, Card card, RunState run, RandomStreams rng) {
        long chips = card.baseChips();
        if (card.enhancement == Enhancement.STONE) chips += 50;
        if (card.enhancement == Enhancement.BONUS) chips += 30;
        if (chips != 0) {
            acc.chips += chips;
            log(acc, card.toString(), "chips", "+" + chips + " Chips");
        }
        if (card.enhancement == Enhancement.MULT) {
            acc.mult += 4;
            log(acc, card.toString(), "mult", "+4 Mult");
        }
        if (card.enhancement == Enhancement.LUCKY) {
            // Probabilistic: 1-in-5 for +20 Mult, 1-in-15 for +$20 (independent streams).
            if (rng.stream("lucky_mult").chance(run.probabilityNumerator, 5)) {
                acc.mult += 20;
                log(acc, card.toString(), "mult", "Lucky! +20 Mult");
            }
            if (rng.stream("lucky_money").chance(run.probabilityNumerator, 15)) {
                run.money += 20;
                log(acc, card.toString(), "dollars", "Lucky! +$20");
            }
        }
        if (card.enhancement == Enhancement.GLASS) {
            acc.mult *= 2;
            log(acc, card.toString(), "xmult", "x2 Mult");
            if (rng.stream("glass").chance(1, 4)) {
                card.destroyed = true;
                acc.destroyed.add(card);
            }
        }
        applyCardEdition(acc, card);
        if (card.seal == Seal.GOLD) {
            run.money += 3;
            log(acc, card.toString(), "dollars", "+$3 (Gold Seal)");
        }
    }

    private void applyCardEdition(Acc acc, Card card) {
        switch (card.edition) {
            case FOIL -> { acc.chips += 50; log(acc, card.toString(), "chips", "+50 Chips (Foil)"); }
            case HOLOGRAPHIC -> { acc.mult += 10; log(acc, card.toString(), "mult", "+10 Mult (Holo)"); }
            case POLYCHROME -> { acc.mult *= 1.5; log(acc, card.toString(), "xmult", "x1.5 Mult (Poly)"); }
            default -> { }
        }
    }

    /** Held-in-hand card effects (Steel x1.5 mult while held). */
    private void applyCardHeld(Acc acc, Card card) {
        if (card.enhancement == Enhancement.STEEL) {
            acc.mult *= 1.5;
            log(acc, card.toString(), "xmult", "x1.5 Mult (Steel)");
        }
    }

    /** Apply a joker effect's fields in fixed order: chips → mult → dollars → hMult → xMult. */
    private void apply(Acc acc, JokerEffect e, String defaultSource) {
        if (e == null) return;
        String src = e.source != null ? e.source : defaultSource;
        if (e.chips != 0) {
            acc.chips += e.chips;
            log(acc, src, "chips", e.message != null ? e.message : "+" + e.chips + " Chips");
        }
        if (e.mult != 0) {
            acc.mult += e.mult;
            log(acc, src, "mult", e.message != null ? e.message : "+" + e.mult + " Mult");
        }
        if (e.dollars != 0) {
            acc.log.add(new ReplayEntry(src, "dollars", "+$" + e.dollars, acc.chips, acc.mult));
        }
        if (e.hMult != 0) {
            acc.mult += e.hMult;
            log(acc, src, "mult", "+" + e.hMult + " Mult");
        }
        if (e.xMult != null) {
            acc.mult *= e.xMult;
            log(acc, src, "xmult", e.message != null ? e.message : "x" + e.xMult + " Mult");
        }
    }

    private static boolean containsIdentity(List<Card> list, Card c) {
        for (Card x : list) if (x == c) return true;
        return false;
    }

    private void log(Acc acc, String source, String kind, String text) {
        acc.log.add(new ReplayEntry(source, kind, text, acc.chips, acc.mult));
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
