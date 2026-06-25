package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.CardModifiers;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Seal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The safety net for the three card-modifier enums — the gap the other coverage nets (jokers, bosses,
 * decks, vouchers, tags, consumables) all had but seals/enhancements/editions did NOT: nothing failed the
 * build when an enum value carried no behavior. Now every value of {@link Enhancement}/{@link Edition}/
 * {@link Seal} must be <i>classified</i>: either it carries scoring data in {@link CardModifiers}
 * (the {@code Effect.Score} vocabulary the scorer interprets), or it is listed in a {@code STRUCTURAL}
 * set naming where its non-scoring behavior lives (run-loop economy, hand-eval, a held/discard hook).
 * A new value — or a forgotten wiring — that fits neither FAILS here. The staleness check keeps the
 * exemption lists honest: a value that gains scoring data must leave its STRUCTURAL set.
 */
class CardModifierCoverageTest {

    // --- Enhancements ---------------------------------------------------------------------------------
    /** Non-scoring enhancements, with where their behavior actually lives. */
    private static final Set<Enhancement> ENH_STRUCTURAL = Set.of(
            Enhancement.NONE,  // the plain card — deliberately inert
            Enhancement.GOLD,  // +$3 at end of round if held — economy, GameEvents.endOfRound (not per-score)
            Enhancement.WILD); // counts as every suit — HandEvaluator flushes / Card.isSuit (not a magnitude)

    @Test
    void everyEnhancementIsClassified() {
        for (Enhancement e : Enhancement.values()) {
            boolean wired = CardModifiers.ENHANCEMENT.containsKey(e)
                    || CardModifiers.HELD.containsKey(e)
                    || CardModifiers.PROBABILISTIC.containsKey(e)
                    || ENH_STRUCTURAL.contains(e);
            assertThat(wired)
                    .as("enhancement '%s' is unclassified — give it CardModifiers data, or list it as STRUCTURAL", e)
                    .isTrue();
        }
    }

    @Test
    void enhancementStructuralMarkersAreNotStale() {
        for (Enhancement e : ENH_STRUCTURAL) {
            if (e == Enhancement.NONE) continue; // NONE is genuinely inert everywhere
            boolean hasScoringData = CardModifiers.ENHANCEMENT.containsKey(e)
                    || CardModifiers.HELD.containsKey(e) || CardModifiers.PROBABILISTIC.containsKey(e);
            assertThat(hasScoringData)
                    .as("enhancement '%s' now carries scoring data — remove it from ENH_STRUCTURAL", e).isFalse();
        }
    }

    // --- Editions -------------------------------------------------------------------------------------
    /** Non-scoring editions. */
    private static final Set<Edition> ED_STRUCTURAL = Set.of(
            Edition.NONE,      // no edition
            Edition.NEGATIVE); // +1 joker/consumable slot — shop/joker logic, not a scoring magnitude

    @Test
    void everyEditionIsClassified() {
        for (Edition e : Edition.values()) {
            boolean wired = CardModifiers.EDITION.containsKey(e) || ED_STRUCTURAL.contains(e);
            assertThat(wired)
                    .as("edition '%s' is unclassified — give it CardModifiers.EDITION data, or list it as STRUCTURAL", e)
                    .isTrue();
        }
    }

    @Test
    void editionStructuralMarkersAreNotStale() {
        for (Edition e : ED_STRUCTURAL) {
            if (e == Edition.NONE) continue;
            assertThat(CardModifiers.EDITION.containsKey(e))
                    .as("edition '%s' now carries scoring data — remove it from ED_STRUCTURAL", e).isFalse();
        }
    }

    // --- Seals ----------------------------------------------------------------------------------------
    /** Seals whose effect is run-loop, not per-card scoring. */
    private static final Set<Seal> SEAL_STRUCTURAL = Set.of(
            Seal.NONE,    // no seal
            Seal.BLUE,    // held at round end -> creates the round's Planet (Run.applyBlueSeals)
            Seal.PURPLE); // discarded -> creates a random Tarot (Run.applyPurpleSeals)

    @Test
    void everySealIsClassified() {
        for (Seal s : Seal.values()) {
            boolean wired = CardModifiers.SEAL.containsKey(s) || SEAL_STRUCTURAL.contains(s);
            assertThat(wired)
                    .as("seal '%s' is unclassified — give it CardModifiers.SEAL data, or list it as STRUCTURAL", s)
                    .isTrue();
        }
    }

    @Test
    void sealStructuralMarkersAreNotStale() {
        for (Seal s : SEAL_STRUCTURAL) {
            if (s == Seal.NONE) continue;
            assertThat(CardModifiers.SEAL.containsKey(s))
                    .as("seal '%s' now carries scoring data — remove it from SEAL_STRUCTURAL", s).isFalse();
        }
    }
}
