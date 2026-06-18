package com.balatro.engine.rng;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The game-long queue determinism shape (BMP parity): two players seeded the same
 * see the same sequence; cursors advance independently; categories don't shift
 * each other; and block/skip consumes past unacceptable items rather than
 * re-rolling. These are the fairness invariants the whole queue model rests on.
 */
class GameQueueTest {

    private static QueueSet set(String seed) {
        return new QueueSet(new RandomStreams(seed));
    }

    private static GameQueue<Integer> ints(QueueSet qs, String key) {
        return qs.queue(key, r -> r.nextInt(100));
    }

    @Test
    void sameSeedSameSequenceForBothPlayers() {
        GameQueue<Integer> p1 = ints(set("DUEL"), "jokers");
        GameQueue<Integer> p2 = ints(set("DUEL"), "jokers");
        assertThat(p1.take(20)).isEqualTo(p2.take(20)); // identical content surface
    }

    @Test
    void differentSeedsDiverge() {
        assertThat(ints(set("A"), "jokers").take(20))
                .isNotEqualTo(ints(set("B"), "jokers").take(20));
    }

    @Test
    void cursorsAdvanceIndependently() {
        QueueSet shared = set("DUEL");
        GameQueue<Integer> q = ints(shared, "jokers");
        // Player who rerolls more is simply further along the SAME sequence.
        List<Integer> firstThree = q.take(3);
        List<Integer> nextThree = q.take(3);
        assertThat(firstThree).isNotEqualTo(nextThree);
        // A fresh player on the same seed re-derives the same first six.
        GameQueue<Integer> other = ints(set("DUEL"), "jokers");
        List<Integer> six = other.take(6);
        assertThat(six.subList(0, 3)).isEqualTo(firstThree);
        assertThat(six.subList(3, 6)).isEqualTo(nextThree);
    }

    @Test
    void separateCategoriesDoNotShiftEachOther() {
        QueueSet a = set("DUEL");
        GameQueue<Integer> jokersThenPlanets = ints(a, "jokers");
        jokersThenPlanets.take(5); // consume jokers
        List<Integer> planetsA = ints(a, "planets").take(5);

        // Consuming jokers first must not change the planets sequence.
        QueueSet b = set("DUEL");
        List<Integer> planetsB = ints(b, "planets").take(5);
        assertThat(planetsA).isEqualTo(planetsB);
    }

    @Test
    void nextWhereSkipsPastBlockedItems() {
        // A queue whose raw values are 0..n; "block" odds -> only evens appear,
        // and the cursor advances past the skipped odds (no re-roll).
        int[] counter = {0};
        GameQueue<Integer> q = new GameQueue<>(() -> counter[0]++);
        assertThat(q.nextWhere(v -> v % 2 == 0)).isEqualTo(0);
        assertThat(q.nextWhere(v -> v % 2 == 0)).isEqualTo(2); // skipped 1
        assertThat(q.nextWhere(v -> v % 2 == 0)).isEqualTo(4); // skipped 3
        assertThat(q.cursor()).isEqualTo(5); // consumed 0,1,2,3,4
    }

    @Test
    void peekDoesNotConsume() {
        GameQueue<Integer> q = ints(set("DUEL"), "jokers");
        Integer peeked = q.peek();
        assertThat(q.next()).isEqualTo(peeked);
        assertThat(q.cursor()).isEqualTo(1);
    }
}
