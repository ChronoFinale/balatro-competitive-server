package com.balatro.engine.hand;

import com.balatro.engine.card.Card;
import java.util.List;

/**
 * Result of hand detection: the top poker-hand category and exactly which played
 * cards compose it (the "scoring hand" before Stone/Splash adjustments, which
 * the ScoringEngine applies).
 */
public record HandResult(HandType type, List<Card> scoringCards) {
}
