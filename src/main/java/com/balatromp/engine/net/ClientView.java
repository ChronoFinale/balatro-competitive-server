package com.balatromp.engine.net;

import java.util.List;
import java.util.Map;

/**
 * The full, authoritative state a client is allowed to render — and nothing more.
 * This record is the info-hiding boundary made structural: there is intentionally
 * NO deck, NO seed, NO RNG state, NO future shop/blind here. A tampered client
 * cannot read what was never sent (spec §8).
 *
 * For multiplayer, an {@code OpponentSummary} (score/handsLeft/lives only) gets
 * added alongside — never the opponent's hand, deck, or seed.
 */
public record ClientView(
        int ante,
        String blind,
        long requirement,
        long roundScore,
        int handsLeft,
        int discardsLeft,
        int money,
        int handSize,
        String phase,
        List<CardView> hand,
        List<Map<String, Object>> jokers,
        List<Map<String, Object>> shop,
        int rerollCost,
        String boss,
        String bossEffect,
        List<Map<String, Object>> shopPlanets,
        List<Map<String, Object>> shopConsumables,
        List<Map<String, Object>> consumables,
        Map<String, Object> handLevels,
        Map<String, Object> deckStats) {
}
