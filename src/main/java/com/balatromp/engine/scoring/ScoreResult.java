package com.balatromp.engine.scoring;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.hand.HandType;
import java.util.List;

/**
 * Authoritative result of scoring one played hand. {@code score = chips * mult}.
 * This (plus {@link #replayLog}) is what gets sent to the client — the score is
 * computed here, never received from the client (spec §7).
 *
 * <p>Score is a double for the slice; real Balatro scores overflow f64 at high
 * antes, so this becomes a big-number type later (spec §8 open item / InsaneInt).
 */
public record ScoreResult(
        HandType handType,
        long chips,
        double mult,
        double score,
        List<ReplayEntry> replayLog,
        List<Card> destroyed) {
}
