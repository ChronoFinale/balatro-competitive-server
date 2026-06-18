package com.balatro.engine.intent;

import java.util.List;

/**
 * What a client is allowed to ask for. The client sends INTENTS, never outcomes
 * (spec §7): "play these card slots", not "I scored X". The server validates and
 * computes everything. Card indices reference the player's current hand.
 */
public sealed interface Intent permits Intent.PlayHand, Intent.Discard {

    record PlayHand(List<Integer> cardIndices) implements Intent {}

    record Discard(List<Integer> cardIndices) implements Intent {}
}
