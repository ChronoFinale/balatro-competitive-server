package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.DeckCatalog;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.RunAction;
import com.balatro.engine.net.ClientView;
import com.balatro.engine.net.ServerUpdate;
import com.balatro.engine.state.Ruleset;
import com.balatro.engine.state.Stake;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A run is a deterministic <b>fold over its accepted actions</b>. These tests pin the three properties
 * everything else (save/restore, history UI, "what if?") hangs off of:
 * <ol>
 *   <li><b>Replay determinism</b> — folding the same actions onto the same identity reproduces the run.</li>
 *   <li><b>What-if divergence</b> — swap one action and the replayed run differs.</li>
 *   <li><b>Only accepted actions are logged</b> — a rejected action leaves the history untouched.</li>
 * </ol>
 */
class RunReplayTest {

    private static final Ruleset RULES = Ruleset.standard();
    private static final String SEED = "REPLAY1";
    private static final Stake STAKE = Stake.WHITE;
    private static final DeckCatalog.DeckType DECK = DeckCatalog.get("d_base");

    private static Run fresh() {
        return new Run(RULES, SEED, STAKE, DECK);
    }

    /** A small but representative sequence: select the blind, play, discard, play again. */
    private static List<RunAction> sampleActions() {
        return List.of(
                new RunAction.SelectBlind(),
                new RunAction.PlayHand(List.of(0, 1, 2, 3, 4)),
                new RunAction.Discard(List.of(0, 1, 2)),
                new RunAction.PlayHand(List.of(0, 1, 2, 3, 4)));
    }

    @Test
    void replayReconstructsIdenticalState() {
        // Drive a run live, capturing its action log...
        Run live = fresh();
        sampleActions().forEach(live::apply);
        List<RunAction> history = live.actionLog();

        // ...then re-fold that exact log onto a fresh run of the same identity.
        Run replayed = Run.replay(RULES, SEED, STAKE, DECK, history);

        assertThat(replayed.actionLog()).isEqualTo(history);
        // The whole authoritative view is reproduced — every gameplay-significant field. We canonicalize
        // card uids because those come from a process-global counter (Ids), not the seed: they're
        // incidental object identity (like a memory address), so the offset shifts run-to-run while the
        // logical state is identical. (Absolute uids inside actions — UseConsumable.targets — are a
        // separate cross-process-replay caveat, documented on RunAction.)
        assertThat(canonical(replayed.view())).isEqualTo(canonical(live.view()));
    }

    /** The view with card uids masked — i.e. the logical game state without the random per-card UUIDs. */
    private static String canonical(ClientView v) {
        return v.toString().replaceAll("uid=[0-9a-fA-F-]+", "uid=#");
    }

    @Test
    void everyAcceptedActionWasLoggedInOrder() {
        Run live = fresh();
        List<RunAction> actions = sampleActions();
        actions.forEach(live::apply);
        // This sample is constructed so every action is accepted, so the log mirrors it exactly.
        assertThat(live.actionLog()).isEqualTo(actions);
    }

    @Test
    void whatIfSwappingOneActionDivergesTheRun() {
        ClientView original = Run.replay(RULES, SEED, STAKE, DECK, sampleActions()).view();

        // What if the very first hand had been a different five cards?
        List<RunAction> swapped = List.of(
                new RunAction.SelectBlind(),
                new RunAction.PlayHand(List.of(3, 4, 5, 6, 7)),
                new RunAction.Discard(List.of(0, 1, 2)),
                new RunAction.PlayHand(List.of(0, 1, 2, 3, 4)));
        ClientView whatIf = Run.replay(RULES, SEED, STAKE, DECK, swapped).view();

        assertThat(canonical(whatIf)).isNotEqualTo(canonical(original));
    }

    @Test
    void rejectedActionsAreNotLogged() {
        Run run = fresh();

        // Buying a shop item during blind-select is rejected; nothing should be recorded.
        ServerUpdate bad = run.apply(new RunAction.BuyShopItem(0));
        assertThat(bad.accepted()).isFalse();
        assertThat(run.actionLog()).isEmpty();

        // A valid action lands, then another rejected one (selecting the blind twice) is dropped.
        ServerUpdate good = run.apply(new RunAction.SelectBlind());
        assertThat(good.accepted()).isTrue();
        ServerUpdate dup = run.apply(new RunAction.SelectBlind());
        assertThat(dup.accepted()).isFalse();

        assertThat(run.actionLog()).containsExactly(new RunAction.SelectBlind());
    }
}
