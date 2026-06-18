package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Seal;
import com.balatro.engine.consumable.TarotCatalog;
import com.balatro.engine.game.PlanetCatalog;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The lifecycle seals (Blue/Purple), as opposed to the scoring-time seals (Red/Gold in
 * {@link SealScoringTest}). Blue creates a Planet for the round's last hand when held at end of round;
 * Purple creates a Tarot when discarded. Both respect "must have room".
 */
class SealLifecycleTest {

    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    private static Run activeBlind() {
        Run run = new Run(Ruleset.standard(), "SL", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_joker"));
        run.selectBlind(); // enter the blind with a hand dealt, before any play
        return run;
    }

    @Test
    void purpleSealCreatesATarotWhenDiscarded() {
        Run run = activeBlind();
        run.state.hand.get(0).seal = Seal.PURPLE;
        int before = run.state.consumables.size();
        run.play(new Intent.Discard(List.of(0)));
        assertThat(run.state.consumables.size()).isEqualTo(before + 1);
        assertThat(TarotCatalog.get(run.state.consumables.get(before))).isNotNull(); // it's a real Tarot
    }

    @Test
    void blueSealHeldAtRoundEndCreatesThePlanetForTheLastHand() {
        Run run = activeBlind();
        run.requirement = 1;                       // any play clears the blind
        run.state.hand.get(5).seal = Seal.BLUE;    // a card NOT in the played 0..4 — stays held
        int before = run.state.consumables.size();
        run.play(FIVE);                            // clears -> winBlind -> Blue Seal fires
        assertThat(run.state.consumables.size()).isEqualTo(before + 1);
        assertThat(PlanetCatalog.get(run.state.consumables.get(before)))
                .as("the created consumable is a Planet").isNotNull();
    }

    @Test
    void blueSealFizzlesWithoutConsumableRoom() {
        Run run = activeBlind();
        run.requirement = 1;
        run.state.hand.get(5).seal = Seal.BLUE;
        run.state.consumables.clear();
        while (run.state.consumables.size() < run.state.consumableSlots) {
            run.state.consumables.add("c_fool"); // fill every slot
        }
        int full = run.state.consumables.size();
        run.play(FIVE);
        assertThat(run.state.consumables.size()).as("no room: the Planet is lost").isEqualTo(full);
    }
}
