package com.balatro.engine.intent;

import com.balatro.engine.card.Card;
import com.balatro.engine.scoring.ScoreResult;
import java.util.List;

/**
 * The authoritative response to an intent: accepted or rejected, plus the new
 * visible state. For a play, {@code score} is server-computed (never trusted
 * from the client). {@code hand} is the post-action hand the client should now
 * render.
 */
public record IntentResult(
        boolean ok,
        String error,
        ScoreResult score,
        int handsLeft,
        int discardsLeft,
        long roundScore,
        List<Card> hand) {

    public static IntentResult rejected(String error) {
        return new IntentResult(false, error, null, 0, 0, 0, List.of());
    }
}
