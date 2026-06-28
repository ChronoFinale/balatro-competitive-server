package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.net.Matchmaker;
import com.balatro.engine.net.Matchmaker.Pairing;
import com.balatro.engine.net.Matchmaker.Waiter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The pure matchmaking decision: closest-MMR adjacency pairing, with tolerance that widens by wait time. */
class MatchmakerTest {

    private static Waiter w(String id, double mmr) {
        return new Waiter(id, mmr, 0);
    }

    @Test
    void pairsTheClosestRatingsNotJustAnyTwo() {
        // 1000 and 1050 are within tolerance; 5000 is the odd one out and stays unpaired.
        List<Pairing> pairs = Matchmaker.pair(List.of(w("a", 1000), w("b", 1050), w("c", 5000)), 150, 0);
        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0)).isEqualTo(new Pairing("a", "b"));
    }

    @Test
    void doesNotPairRatingsBeyondTolerance() {
        assertThat(Matchmaker.pair(List.of(w("a", 1000), w("b", 2000)), 150, 0)).isEmpty();
    }

    @Test
    void wideningWithWaitTimeEventuallyPairsAnIsolatedRating() {
        // gap 1000; base 150 + 500/s. At 0s no match; at 2s tolerance is 1150 >= 1000 -> pairs.
        var fresh = List.of(new Waiter("a", 1000, 0), new Waiter("b", 2000, 0));
        assertThat(Matchmaker.pair(fresh, 150, 500)).isEmpty();
        var waited = List.of(new Waiter("a", 1000, 2), new Waiter("b", 2000, 2));
        assertThat(Matchmaker.pair(waited, 150, 500)).hasSize(1);
    }

    @Test
    void pairsManyAdjacentAndLeavesTheOddOneOut() {
        List<Pairing> pairs = Matchmaker.pair(
                List.of(w("a", 1000), w("b", 1010), w("c", 1020), w("d", 1030), w("e", 1040)), 150, 0);
        assertThat(pairs).hasSize(2); // 5 players -> 2 pairs, one waits
    }

    @Test
    void isOrderIndependentSortingByMmr() {
        // Input order shuffled; pairing is by MMR adjacency, so the close pair still forms.
        List<Pairing> pairs = Matchmaker.pair(List.of(w("c", 5000), w("b", 1050), w("a", 1000)), 150, 0);
        assertThat(pairs).containsExactly(new Pairing("a", "b"));
    }
}
