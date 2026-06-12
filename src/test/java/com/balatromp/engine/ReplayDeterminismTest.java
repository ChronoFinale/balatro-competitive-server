package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.state.Deck;
import com.balatromp.engine.state.Ruleset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Whole-engine replay determinism: a run is a pure function of (seed + action sequence). Driving the
 * SAME seed through the SAME scripted actions must produce a byte-identical trajectory — across deals,
 * shop generation, rerolls, and blind progression, not just one hand (DeterminismTest covers a single
 * hand). This is the "replay" half of the eventual replay-diff harness, and a guard against accidental
 * nondeterminism creeping in (e.g. iterating a HashMap before an RNG pick — the cross-OS {@code pairs()}
 * bug BMP had to fix). High-confidence: it exercises our real engine, no external oracle.
 */
class ReplayDeterminismTest {

    private static final Ruleset STD = Ruleset.standard();
    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    /** Lucky Ace of Hearts ×n: a 5-card hand is a high-scoring flush AND every card rolls the seed-
     *  dependent Lucky queues, so trajectories diverge by seed (not constant). */
    private static Deck luckyDeck(int n) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cards.add(new Card(Rank.ACE, Suit.HEARTS, Enhancement.LUCKY, Edition.NONE, Seal.NONE));
        }
        return Deck.of(cards);
    }

    /** A compact, deterministic snapshot of everything a replay should reproduce. */
    private static String sig(Run run) {
        StringBuilder sb = new StringBuilder();
        sb.append(run.phase).append('|').append(run.ante).append('|').append(run.blind).append('|')
                .append(run.state.money).append('|').append(run.state.roundScore).append('|')
                .append(run.state.handsLeft).append('|').append(run.state.handSize).append('|')
                .append(run.state.deckComposition.size()).append("|J:");
        for (Joker j : run.state.jokers()) {
            sb.append(j.key()).append('/').append(run.state.jokerEdition(j)).append(';');
        }
        sb.append("|C:").append(String.join(",", run.state.consumables)).append("|S:");
        if (run.shop != null) {
            run.shop.items().forEach(it -> sb.append(it.key()).append('/').append(it.edition()).append(';'));
            sb.append("V:").append(String.join(",", run.shop.vouchers()));
        }
        return sb.toString();
    }

    /** Drive a fixed script; capture a signature after every step. */
    private static List<String> playScript(String seed) {
        Run run = new Run(STD, seed, luckyDeck(80), jokers());
        List<String> trajectory = new ArrayList<>();
        for (int step = 0; step < 14; step++) {
            switch (run.phase) {
                case BLIND_SELECT, BLIND_ACTIVE -> run.play(FIVE);
                case SHOP -> { run.reroll(); run.proceed(); } // exercise shop-reroll RNG, then advance
                default -> { /* RUN_WON / RUN_LOST / FAILED — stop driving, keep snapshotting */ }
            }
            trajectory.add(sig(run));
        }
        return trajectory;
    }

    @Test
    void sameSeedAndScriptReplayByteIdentically() {
        assertThat(playScript("REPLAY-SEED")).isEqualTo(playScript("REPLAY-SEED"));
    }

    @Test
    void differentSeedsDivergeSomewhereInTheTrajectory() {
        // Seed drives deals, Lucky procs, and shop offerings — two seeds must differ at some step.
        assertThat(playScript("REPLAY-SEED")).isNotEqualTo(playScript("OTHER-SEED"));
    }
}
