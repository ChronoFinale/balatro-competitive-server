package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Match;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.state.Ruleset;
import com.balatromp.engine.state.RulesetStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Asteroid MP planet delevels the NEMESIS's highest-level poker hand (resolved by
 * the Match after the action). Ties go to the higher-ranked hand; nothing drops below
 * level 1. Drives a Match directly (no transport), the lightweight harness for MP
 * cross-player mechanics.
 */
class AsteroidMpTest {

    /** A started 2-player match on the curated "Standard" ruleset; returns it ready to play. */
    private Match startedMatch(Path dir) {
        Match match = new Match("CODE", "seed", Ruleset.standard(), new RulesetStore(dir), (sid, payload) -> { });
        match.setHost("h", "ph");
        match.setGuestAndStart("g", "pg"); // -> AGREEING
        match.propose("h", "Standard");
        match.respond("g", true);          // -> PLAYING, both Runs created
        return match;
    }

    @Test
    void asteroidDelevelsTheNemesisHighestHand(@TempDir Path dir) {
        Match match = startedMatch(dir);
        Run host = match.runOf("h");
        Run guest = match.runOf("g");
        assertThat(host).isNotNull();
        assertThat(guest).isNotNull();

        guest.state.levelUpHand(HandType.PAIR);
        guest.state.levelUpHand(HandType.PAIR); // guest PAIR -> level 3 (the highest)
        assertThat(guest.state.handLevel(HandType.PAIR)).isEqualTo(3);

        host.state.consumables.add("c_asteroid");
        assertThat(host.useConsumable(0)).isNull(); // host uses Asteroid (queues a nemesis delevel)
        match.onAction("h");                          // Match applies it to the guest

        assertThat(guest.state.handLevel(HandType.PAIR)).isEqualTo(2);
    }

    @Test
    void tieDelevelsTheHigherRankedHand(@TempDir Path dir) {
        Match match = startedMatch(dir);
        Run host = match.runOf("h");
        Run guest = match.runOf("g");
        guest.state.levelUpHand(HandType.PAIR);      // both at level 2 (a tie)
        guest.state.levelUpHand(HandType.TWO_PAIR);

        host.state.consumables.add("c_asteroid");
        host.useConsumable(0);
        match.onAction("h");

        assertThat(guest.state.handLevel(HandType.TWO_PAIR)).isEqualTo(1); // higher hand deleveled
        assertThat(guest.state.handLevel(HandType.PAIR)).isEqualTo(2);     // the tie-loser untouched
    }

    @Test
    void asteroidNeverDropsBelowLevelOne(@TempDir Path dir) {
        Match match = startedMatch(dir);
        Run host = match.runOf("h");
        Run guest = match.runOf("g");
        // All hands at base level 1; an Asteroid changes nothing (floored).
        host.state.consumables.add("c_asteroid");
        host.useConsumable(0);
        match.onAction("h");
        assertThat(guest.state.handLevel(HandType.PAIR)).isEqualTo(1);
    }
}
