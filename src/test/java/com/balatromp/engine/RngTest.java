package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.seq;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.rng.RandomStreams;
import java.util.List;
import org.junit.jupiter.api.Test;

class RngTest {

    @Test
    void sameSeedSameShuffleAndStreamsAreIndependent() {
        RandomStreams a = new RandomStreams("SEED-A");
        RandomStreams b = new RandomStreams("SEED-A");
        // Draining an unrelated stream on `a` must not affect the "shuffle" stream.
        for (int i = 0; i < 100; i++) a.stream("noise").nextLong();

        List<Integer> la = seq(20);
        List<Integer> lb = seq(20);
        a.shuffle(la, "shuffle");
        b.shuffle(lb, "shuffle");

        assertThat(la).isEqualTo(lb);
    }

    @Test
    void differentSeedsProduceDifferentShuffles() {
        List<Integer> la = seq(20);
        List<Integer> lc = seq(20);
        new RandomStreams("SEED-A").shuffle(la, "shuffle");
        new RandomStreams("SEED-C").shuffle(lc, "shuffle");
        assertThat(lc).isNotEqualTo(la);
    }
}
