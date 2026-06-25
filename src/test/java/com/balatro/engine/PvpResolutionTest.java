package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Match;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Ruleset;
import com.balatro.engine.state.RulesetStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The PvP/Attrition RESOLUTION transcript — the part {@code MatchTest} never reaches (it stops at setup).
 * Drives two Runs into a Nemesis blind, runs them out of hands, and asserts the Match's resolution: the
 * scores it reports, who loses the life, and the tie case. The duel-to-result coverage that was missing.
 */
class PvpResolutionTest {

    /** Capturing transport: collects every (sessionId, payload) the Match sends. */
    private final List<Object[]> sent = new ArrayList<>();

    private Match startedMatch(Path dir) {
        BiConsumer<String, Object> capture = (sid, payload) -> sent.add(new Object[]{sid, payload});
        Match match = new Match("CODE", "seed", Ruleset.standard(), new RulesetStore(dir), capture);
        match.setHost("h", "ph");
        match.setGuestAndStart("g", "pg");
        match.propose("h", "Standard");
        match.respond("g", true); // -> PLAYING, both Runs created
        return match;
    }

    /** Skip ante-1's Small and Big, select the boss (a Nemesis blind with pvpFromAnte=1), play out of hands. */
    private static void driveToPvpPending(Run run) {
        run.pvpFromAnte = 1;
        run.skipBlind();   // Small -> Big
        run.skipBlind();   // Big -> Boss
        run.selectBlind(); // enter the Nemesis blind
        assertThat(run.inPvpBlind()).as("ante-1 boss is a Nemesis blind").isTrue();
        for (int i = 0; i < 12 && run.phase != Run.Phase.PVP_PENDING; i++) {
            run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        }
        assertThat(run.phase).as("ran out of hands in the Nemesis blind").isEqualTo(Run.Phase.PVP_PENDING);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultFor(String session) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i)[0].equals(session) && sent.get(i)[1] instanceof Map<?, ?> m
                    && "pvpResult".equals(m.get("type"))) {
                return (Map<String, Object>) m;
            }
        }
        throw new AssertionError("no pvpResult sent to " + session);
    }

    @Test
    void higherScoreWinsAndTheLoserLosesALife(@TempDir Path dir) {
        Match match = startedMatch(dir);
        Run host = match.runOf("h"), guest = match.runOf("g");
        driveToPvpPending(host);
        driveToPvpPending(guest);

        host.state.roundScore = guest.state.roundScore + 100; // force the host ahead
        long h = host.state.roundScore, g = guest.state.roundScore;
        sent.clear();
        match.onAction("h"); // both locked -> resolve

        Map<String, Object> hostR = resultFor("h"), guestR = resultFor("g");
        assertThat(hostR.get("outcome")).isEqualTo("win");
        assertThat(guestR.get("outcome")).isEqualTo("lose");
        assertThat(((Number) hostR.get("yourScore")).longValue()).isEqualTo(h);
        assertThat(((Number) hostR.get("oppScore")).longValue()).isEqualTo(g);
        assertThat(((Number) guestR.get("yourScore")).longValue()).isEqualTo(g); // each sees its own as "yours"
        assertThat(((Number) hostR.get("yourLives")).intValue()).isEqualTo(4);   // winner keeps lives
        assertThat(((Number) guestR.get("yourLives")).intValue()).isEqualTo(3);  // loser drops one
    }

    /** Enter the Nemesis blind and play ONE hand — still has hands left (BLIND_ACTIVE, not locked). */
    private static void driveIntoPvpBlindStillPlaying(Run run) {
        run.pvpFromAnte = 1;
        run.skipBlind();
        run.skipBlind();
        run.selectBlind();
        assertThat(run.inPvpBlind()).isTrue();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        assertThat(run.phase).as("still has hands left").isEqualTo(Run.Phase.BLIND_ACTIVE);
    }

    @Test
    void aPlayerOutOfHandsAndBEHINDLosesImmediatelyEvenIfTheOtherIsStillPlaying(@TempDir Path dir) {
        Match match = startedMatch(dir);
        Run host = match.runOf("h"), guest = match.runOf("g");
        driveToPvpPending(host);             // host out of hands (locked)
        driveIntoPvpBlindStillPlaying(guest); // guest still playing
        host.state.roundScore = guest.state.roundScore - 50; // host is behind
        sent.clear();
        match.onAction("h");

        assertThat(resultFor("h").get("outcome")).isEqualTo("lose"); // behind + out of hands = dead
        assertThat(((Number) resultFor("h").get("yourLives")).intValue()).isEqualTo(3);
        assertThat(((Number) resultFor("g").get("yourLives")).intValue()).isEqualTo(4);
    }

    @Test
    void aPlayerOutOfHandsButAHEADWaitsForTheOtherToFinish(@TempDir Path dir) {
        Match match = startedMatch(dir);
        Run host = match.runOf("h"), guest = match.runOf("g");
        driveToPvpPending(host);
        driveIntoPvpBlindStillPlaying(guest);
        host.state.roundScore = guest.state.roundScore + 50; // host is AHEAD — needn't be decided yet
        sent.clear();
        match.onAction("h");

        // The match is NOT resolved — the guest still has hands to (try to) catch up.
        assertThat(sent.stream().noneMatch(e -> e[1] instanceof Map<?, ?> m && "pvpResult".equals(m.get("type"))))
                .as("no result while the ahead player waits").isTrue();
        assertThat(guest.inPvpBlind()).as("guest still in the blind").isTrue();
    }

    @Test
    void aTieCostsNobodyALife(@TempDir Path dir) {
        Match match = startedMatch(dir);
        Run host = match.runOf("h"), guest = match.runOf("g");
        driveToPvpPending(host);
        driveToPvpPending(guest);

        host.state.roundScore = guest.state.roundScore; // exact tie
        sent.clear();
        match.onAction("h");

        assertThat(resultFor("h").get("outcome")).isEqualTo("tie");
        assertThat(resultFor("g").get("outcome")).isEqualTo("tie");
        assertThat(((Number) resultFor("h").get("yourLives")).intValue()).isEqualTo(4);
        assertThat(((Number) resultFor("g").get("yourLives")).intValue()).isEqualTo(4);
    }
}
