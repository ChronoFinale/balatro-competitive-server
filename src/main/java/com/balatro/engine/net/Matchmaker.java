package com.balatro.engine.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The ranked matchmaking decision — pure and deterministic, so it's testable without sockets. Given the
 * players currently waiting (each with their MMR and how long they've waited), it pairs the closest ratings:
 * sort by MMR and greedily pair adjacent players whose gap is within tolerance. Tolerance widens with the
 * longer-waiting player's time in queue ({@code base + widenPerSecond × waitSeconds}, uncapped), so two
 * close ratings match instantly while an isolated rating still eventually pairs rather than waiting forever.
 *
 * <p>The server ({@link GameServer}) owns the queue + sockets; it builds {@link Waiter}s from live waiters,
 * calls {@link #pair}, and turns each {@link Pairing} into a Match.
 */
public final class Matchmaker {

    /** A player waiting in the queue: a stable id, their MMR, and seconds spent waiting. */
    public record Waiter(String id, double mmr, double waitSeconds) {}

    /** Two ids the matchmaker decided to pair. */
    public record Pairing(String a, String b) {}

    private Matchmaker() {}

    public static List<Pairing> pair(List<Waiter> waiters, double baseTolerance, double widenPerSecond) {
        List<Waiter> sorted = new ArrayList<>(waiters);
        sorted.sort(Comparator.comparingDouble(Waiter::mmr));
        List<Pairing> pairings = new ArrayList<>();
        int i = 0;
        while (i + 1 < sorted.size()) {
            Waiter a = sorted.get(i);
            Waiter b = sorted.get(i + 1);
            double gap = Math.abs(a.mmr() - b.mmr());
            double tolerance = baseTolerance + widenPerSecond * Math.max(a.waitSeconds(), b.waitSeconds());
            if (gap <= tolerance) {
                pairings.add(new Pairing(a.id(), b.id()));
                i += 2; // both consumed
            } else {
                i += 1; // a can't reach its nearest neighbour yet — leave it for a later (wider) round
            }
        }
        return pairings;
    }
}
