package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.rank.RankTier;
import org.junit.jupiter.api.Test;

/** The MMR -> tier/division mapping (display only): band boundaries, division ordering, Master capstone. */
class RankTierTest {

    @Test
    void tierBandBoundaries() {
        assertThat(RankTier.of(0)).isEqualTo(RankTier.BRONZE);
        assertThat(RankTier.of(799)).isEqualTo(RankTier.BRONZE);
        assertThat(RankTier.of(800)).isEqualTo(RankTier.SILVER);
        assertThat(RankTier.of(1000)).isEqualTo(RankTier.GOLD);   // the starting MMR sits in Gold
        assertThat(RankTier.of(1199)).isEqualTo(RankTier.GOLD);
        assertThat(RankTier.of(1200)).isEqualTo(RankTier.PLATINUM);
        assertThat(RankTier.of(1599)).isEqualTo(RankTier.DIAMOND);
        assertThat(RankTier.of(1600)).isEqualTo(RankTier.MASTER);
        assertThat(RankTier.of(9999)).isEqualTo(RankTier.MASTER);
    }

    @Test
    void divisionsRunIVatBottomToIatTop() {
        assertThat(RankTier.display(1000)).isEqualTo("Gold IV"); // bottom of the Gold band
        assertThat(RankTier.display(1100)).isEqualTo("Gold II"); // mid band
        assertThat(RankTier.display(1150)).isEqualTo("Gold I");  // top of the band
        assertThat(RankTier.display(0)).isEqualTo("Bronze IV");
    }

    @Test
    void masterHasNoDivision() {
        assertThat(RankTier.display(1600)).isEqualTo("Master");
        assertThat(RankTier.display(3000)).isEqualTo("Master");
    }
}
