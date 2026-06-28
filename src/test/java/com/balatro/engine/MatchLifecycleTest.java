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
 * R1 match lifecycle: a finished match keeps the pairing so the two players can rematch (same opponents,
 * fresh seed, already-agreed ruleset), and a head-to-head tally accumulates across rematches on the one
 * Match object. Drives real finishes via Attrition (lose all lives), reusing {@link PvpResolutionTest}'s
 * Nemesis-driving approach — no sockets.
 */
class MatchLifecycleTest {

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

    /** Enter the next ante's Nemesis (boss) blind and play out of hands -> PVP_PENDING (locked). */
    private static void advanceToNemesis(Run run) {
        run.pvpFromAnte = 1; // every boss is a Nemesis blind
        if (run.phase == Run.Phase.SHOP) run.proceed(); // leave a prior shop into the next ante
        run.skipBlind();   // Small -> Big
        run.skipBlind();   // Big -> Boss
        run.selectBlind(); // enter the Nemesis blind
        for (int i = 0; i < 12 && run.phase != Run.Phase.PVP_PENDING; i++) {
            run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        }
        assertThat(run.phase).isEqualTo(Run.Phase.PVP_PENDING);
    }

    /** Run Nemesis blinds where {@code loser} always scores 0 until that side is out of lives -> FINISHED. */
    private void driveToFinish(Match match, String loser) {
        String winner = loser.equals("h") ? "g" : "h";
        for (int guard = 0; guard < 6 && match.phase() != Match.Phase.FINISHED; guard++) {
            advanceToNemesis(match.runOf("h"));
            advanceToNemesis(match.runOf("g"));
            match.runOf(loser).state.roundScore = 0;
            match.runOf(winner).state.roundScore = 100_000;
            match.onAction(loser);
        }
        assertThat(match.phase()).isEqualTo(Match.Phase.FINISHED);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastMatchResult() {
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i)[1] instanceof Map<?, ?> m && "matchResult".equals(m.get("type"))) {
                return (Map<String, Object>) m;
            }
        }
        throw new AssertionError("no matchResult sent");
    }

    @Test
    void finishRecordsTheWinnerAndHeadToHead(@TempDir Path dir) {
        Match match = startedMatch(dir);
        driveToFinish(match, "h"); // host loses every Nemesis -> guest wins the match

        Map<String, Object> result = lastMatchResult();
        assertThat(result.get("winner")).isEqualTo("pg");
        @SuppressWarnings("unchecked")
        Map<String, Object> h2h = (Map<String, Object>) result.get("headToHead");
        assertThat(((Number) h2h.get("pg")).intValue()).isEqualTo(1);
        assertThat(((Number) h2h.get("ph")).intValue()).isEqualTo(0);
    }

    @Test
    void rematchNeedsBothSidesThenReplaysTheSamePairingFresh(@TempDir Path dir) {
        Match match = startedMatch(dir);
        Run firstHostRun = match.runOf("h");
        driveToFinish(match, "h");
        assertThat(match.phase()).isEqualTo(Match.Phase.FINISHED);

        // One side asking is not enough.
        assertThat(match.requestRematch("h")).isFalse();
        assertThat(match.phase()).isEqualTo(Match.Phase.FINISHED);
        // The other side agreeing completes it; the caller starts the rematch.
        assertThat(match.requestRematch("g")).isTrue();
        match.startRematch("seed2");

        assertThat(match.phase()).isEqualTo(Match.Phase.PLAYING);
        assertThat(match.hostPlayerId()).isEqualTo("ph"); // same pairing
        assertThat(match.guestPlayerId()).isEqualTo("pg");
        Run secondHostRun = match.runOf("h");
        assertThat(secondHostRun).isNotSameAs(firstHostRun); // fresh Run
        assertThat(secondHostRun.ante).isEqualTo(1);          // reset to the start
    }

    @Test
    void headToHeadAccumulatesAcrossRematches(@TempDir Path dir) {
        Match match = startedMatch(dir);
        driveToFinish(match, "h");            // guest wins match 1
        match.requestRematch("h");
        match.requestRematch("g");
        match.startRematch("seed2");
        driveToFinish(match, "g");            // host wins match 2

        @SuppressWarnings("unchecked")
        Map<String, Object> h2h = (Map<String, Object>) lastMatchResult().get("headToHead");
        assertThat(((Number) h2h.get("ph")).intValue()).isEqualTo(1);
        assertThat(((Number) h2h.get("pg")).intValue()).isEqualTo(1);
    }

    @Test
    void cannotRematchBeforeTheMatchIsFinished(@TempDir Path dir) {
        Match match = startedMatch(dir);
        assertThat(match.requestRematch("h")).isFalse(); // still PLAYING
        assertThat(sent.stream().anyMatch(e -> e[1] instanceof Map<?, ?> m
                && "error".equals(m.get("type")) && String.valueOf(m.get("rejection")).contains("rematch")))
                .isTrue();
    }
}
