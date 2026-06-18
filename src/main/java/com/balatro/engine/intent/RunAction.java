package com.balatro.engine.intent;

import java.util.List;
import java.util.UUID;

/**
 * The complete alphabet of run-mutating actions, as serializable values. A run is a deterministic
 * <b>fold over its actions</b>: given the same {@code (ruleset, seed, stake, deck)} and the same ordered
 * list of accepted {@code RunAction}s, you reconstruct the exact same run ({@code Run.replay}). The run's
 * append-only action log is therefore the run's whole history — enough to save/restore (snapshot = a log
 * prefix), verify (re-fold and compare), and ask "what if?" (re-fold with one action swapped).
 *
 * <p>{@code newRun} is NOT an action — its inputs (ruleset/seed/stake/deck) are the run's identity, the
 * starting point of the fold. Everything after is an action here.
 */
public sealed interface RunAction {

    record SelectBlind() implements RunAction {}

    record SkipBlind() implements RunAction {}

    record PlayHand(List<Integer> cards) implements RunAction {}

    record Discard(List<Integer> cards) implements RunAction {}

    record BuyShopItem(int index) implements RunAction {}

    record Reroll() implements RunAction {}

    record BuyVoucher(int index) implements RunAction {}

    record OpenPack(int index) implements RunAction {}

    record PickPackItem(int index) implements RunAction {}

    record SkipPack() implements RunAction {}

    record SellJoker(int index) implements RunAction {}

    /**
     * {@code targets} are the selected card uids (for Tarots); empty for Planets/Spectrals. The uids are
     * UUIDs ({@code Ids}) — outcome-neutral target handles. Save/restore is snapshot-and-continue (reload
     * the state with its ids intact), so the targets resolve directly; no seed-deterministic id is needed.
     */
    record UseConsumable(int index, List<UUID> targets) implements RunAction {}

    record Proceed() implements RunAction {}
}
