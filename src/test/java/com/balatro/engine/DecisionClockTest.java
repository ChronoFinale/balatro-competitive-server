package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.DecisionClock;
import org.junit.jupiter.api.Test;

/**
 * Executable spec for the "decisions, not strict time" competitive clock. Each test encodes a design
 * property we settled on: deliberation is charged, resolution/animation is free, an animation-dense build
 * is never penalized, and a client-driven pause is self-limiting (capped by the server-canonical max).
 * Time is injected (millis), so these are deterministic.
 */
class DecisionClockTest {

    @Test
    void deliberationDuringAWindowIsCharged() {
        DecisionClock c = new DecisionClock();
        c.open(1_000);          // server presents a decision, no animation before it
        c.close(4_000);         // player acts 3s later
        assertThat(c.accruedMillis()).isEqualTo(3_000);
    }

    @Test
    void animationCreditIsFreeTime_quickReactionCostsNothing() {
        DecisionClock c = new DecisionClock();
        // A 2s canonical animation preceded the window; the player reacts 1.5s in (still mid-animation).
        c.open(1_000, /*credit*/ 2_000, /*cap*/ 5_000);
        c.close(2_500);
        assertThat(c.accruedMillis()).isZero(); // acted within the free animation window
    }

    @Test
    void onlyDeliberationPastTheAnimationIsCharged() {
        DecisionClock c = new DecisionClock();
        c.open(1_000, 2_000, 5_000); // 2s free animation
        c.close(4_000);              // 3s total -> 1s past the animation
        assertThat(c.accruedMillis()).isEqualTo(1_000);
    }

    @Test
    void animationDenseBuildIsNotPenalized() {
        // The Vagabond case: a hand whose resolution animates for a long time. The server credits that
        // canonical duration, so a normal decision after it accrues only the human's thinking, not the show.
        DecisionClock c = new DecisionClock();
        long hugeAnimation = 20_000; // 20s of spawning/retrigger juice
        c.open(1_000, hugeAnimation, /*cap*/ 25_000);
        c.close(1_000 + hugeAnimation + 1_500); // player then deliberates 1.5s
        assertThat(c.accruedMillis()).isEqualTo(1_500);
    }

    @Test
    void creditIsCappedSoClientPauseAbuseIsBounded() {
        // A cheating client claims a 30s animation on a hand the server knows is worth at most 2s.
        DecisionClock c = new DecisionClock();
        c.open(1_000, /*requested*/ 30_000, /*cap*/ 2_000);
        c.close(5_000);                 // elapsed 4s; only 2s credited
        assertThat(c.accruedMillis()).isEqualTo(2_000); // not 0 — abuse clamped to the canonical max
    }

    @Test
    void timeBetweenWindowsIsFree() {
        DecisionClock c = new DecisionClock();
        c.open(1_000);
        c.close(2_000);                 // +1s
        // ...long resolution / cash-out / shop materialize happens here while NO window is open...
        c.open(10_000);                 // next decision presented 8s later
        c.close(11_500);                // +1.5s
        assertThat(c.accruedMillis()).isEqualTo(2_500); // the 8s gap is not charged
    }

    @Test
    void elapsedAsOfIsALiveReadoutThatDoesNotCommit() {
        DecisionClock c = new DecisionClock();
        c.open(1_000, 1_000, 5_000);    // 1s free
        assertThat(c.elapsedAsOf(1_500)).isZero();        // still within the credit
        assertThat(c.elapsedAsOf(3_000)).isEqualTo(1_000); // 2s in -> 1s charged live
        assertThat(c.accruedMillis()).isZero();            // ...but nothing committed until close
        assertThat(c.isOpen()).isTrue();
        c.close(3_000);
        assertThat(c.accruedMillis()).isEqualTo(1_000);
    }

    @Test
    void closeWithoutOpenIsANoOp() {
        DecisionClock c = new DecisionClock();
        c.close(5_000);
        assertThat(c.accruedMillis()).isZero();
        assertThat(c.isOpen()).isFalse();
    }

    @Test
    void negativeOrZeroDurationsNeverChargeNegative() {
        DecisionClock c = new DecisionClock();
        c.open(5_000, -100, -100);      // junk credit/cap clamp to 0
        c.close(4_000);                 // clock moved backwards (skew) -> charge 0, not negative
        assertThat(c.accruedMillis()).isZero();
    }
}
