package com.balatromp.engine.intent;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.game.GameEvents;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoreResult;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates and executes client intents against authoritative state (spec §7).
 * Every intent is checked against the current run before anything happens; an
 * invalid request is rejected, never trusted. This is the choke point that makes
 * the server authoritative.
 */
public final class IntentHandler {

    private final ScoringEngine engine = new ScoringEngine();

    public IntentResult handle(RunState run, RandomStreams rng, Intent intent) {
        if (intent instanceof Intent.PlayHand ph) {
            return playHand(run, rng, ph);
        }
        if (intent instanceof Intent.Discard d) {
            return discard(run, rng, d);
        }
        return IntentResult.rejected("unknown intent");
    }

    private IntentResult playHand(RunState run, RandomStreams rng, Intent.PlayHand ph) {
        if (run.handsLeft <= 0) {
            return IntentResult.rejected("no hands left");
        }
        List<Card> played = resolveSelection(run, ph.cardIndices());
        if (played == null) {
            return IntentResult.rejected("invalid card selection");
        }
        if (played.isEmpty() || played.size() > 5) {
            return IntentResult.rejected("must play 1-5 cards");
        }

        List<Card> held = new ArrayList<>(run.hand);
        held.removeAll(played); // identity removal — cards are distinct objects

        ScoreResult score = engine.score(played, held, run, rng);

        // Commit authoritative state changes. Per-type play tracking is updated AFTER
        // scoring, so Supernova/Card Sharp saw the pre-this-hand counts during the hand.
        run.handsLeft--;
        run.roundScore += Math.round(score.score());
        run.handTypePlays.merge(score.handType(), 1, Integer::sum);
        run.handTypesThisRound.add(score.handType());
        run.hand.removeAll(played);
        if (run.deck != null) {
            run.deck.drawTo(run.hand, run.handSize);
        }

        return new IntentResult(true, null, score, run.handsLeft, run.discardsLeft,
                run.roundScore, new ArrayList<>(run.hand));
    }

    private IntentResult discard(RunState run, RandomStreams rng, Intent.Discard d) {
        if (run.discardsLeft <= 0) {
            return IntentResult.rejected("no discards left");
        }
        List<Card> toDiscard = resolveSelection(run, d.cardIndices());
        if (toDiscard == null) {
            return IntentResult.rejected("invalid card selection");
        }
        if (toDiscard.isEmpty() || toDiscard.size() > 5) {
            return IntentResult.rejected("must discard 1-5 cards");
        }

        // Raise PRE_DISCARD so jokers (e.g. Faceless) can react before resolution.
        GameEvents.preDiscard(run, rng, toDiscard);

        run.discardsLeft--;
        run.discardsUsedThisRound++;
        run.cardsDiscardedTotal += toDiscard.size();
        run.hand.removeAll(toDiscard);
        if (run.deck != null) {
            run.deck.drawTo(run.hand, run.handSize);
        }

        return new IntentResult(true, null, null, run.handsLeft, run.discardsLeft,
                run.roundScore, new ArrayList<>(run.hand));
    }

    /** Validate indices: distinct, in range, and map to the current hand. */
    private List<Card> resolveSelection(RunState run, List<Integer> indices) {
        if (indices == null || indices.isEmpty()) return null;
        Set<Integer> seen = new HashSet<>();
        List<Card> cards = new ArrayList<>();
        for (Integer i : indices) {
            if (i == null || i < 0 || i >= run.hand.size() || !seen.add(i)) {
                return null;
            }
            cards.add(run.hand.get(i));
        }
        return cards;
    }
}
