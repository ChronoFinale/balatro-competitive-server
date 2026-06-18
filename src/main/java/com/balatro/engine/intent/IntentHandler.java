package com.balatro.engine.intent;

import com.balatro.engine.card.Card;
import com.balatro.engine.game.GameEvents;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
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
        // Obelisk: streak of consecutive hands that aren't your most-played hand (pre-this-hand
        // counts), reset when you play the most-played. Updated after scoring, so the value the
        // joker used this hand was the pre-hand streak (shipped -> previews exactly).
        com.balatro.engine.hand.HandType mostPlayed = mostPlayedType(run);
        if (mostPlayed != null && score.handType() == mostPlayed) run.obeliskStreak = 0;
        else run.obeliskStreak++;
        run.handTypePlays.merge(score.handType(), 1, Integer::sum);
        run.handTypesThisRound.add(score.handType());
        run.lastPlayedHandType = score.handType(); // Blue Seal reads this at end of round
        for (Card c : played) c.playedThisAnte = true; // The Pillar: a played card is debuffed if replayed this ante
        run.hand.removeAll(played);
        refill(run);

        return new IntentResult(true, null, score, run.handsLeft, run.discardsLeft,
                run.roundScore, new ArrayList<>(run.hand));
    }

    /** The poker hand played most this run (count > 0), or null if none played yet. */
    private static com.balatro.engine.hand.HandType mostPlayedType(RunState run) {
        com.balatro.engine.hand.HandType best = null;
        int bestN = 0;
        for (var e : run.handTypePlays.entrySet()) {
            if (e.getValue() > bestN) { bestN = e.getValue(); best = e.getKey(); }
        }
        return best;
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

        // Raise PRE_DISCARD so jokers react before resolution: Faceless ($5 on 3+ faces), Trading Card
        // (first single-card discard → destroy it + $3, all expressed as data on the discarded set).
        GameEvents.preDiscard(run, rng, toDiscard);

        run.discardsLeft--;
        run.discardsUsedThisRound++;
        run.cardsDiscardedTotal += toDiscard.size();
        run.hand.removeAll(toDiscard);
        refill(run);

        return new IntentResult(true, null, null, run.handsLeft, run.discardsLeft,
                run.roundScore, new ArrayList<>(run.hand));
    }

    /** Refill the hand after a play/discard: normally up to hand size, but The Serpent
     *  ({@code drawCountOverride > 0}) draws a fixed count instead, so the hand can shrink. */
    private static void refill(RunState run) {
        if (run.deck == null) return;
        if (run.drawCountOverride > 0) {
            run.deck.drawCount(run.hand, run.drawCountOverride);
        } else {
            run.deck.drawTo(run.hand, run.handSize);
        }
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
