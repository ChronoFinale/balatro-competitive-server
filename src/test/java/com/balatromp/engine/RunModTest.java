package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Run;
import com.balatromp.engine.state.Ruleset;
import org.junit.jupiter.api.Test;

/** Passive per-blind run modifiers (Juggler, Burglar, ...) applied at blind start. */
class RunModTest {

    private final Ruleset std = Ruleset.standard();

    @Test
    void jugglerAddsHandSize() {
        Run run = new Run(std, "J", stoneDeck(400), jokers("j_juggler"));
        assertThat(run.state.handSize).isEqualTo(std.handSize() + 1);
    }

    @Test
    void burglarGivesExtraHandsButZeroDiscards() {
        Run run = new Run(std, "B", stoneDeck(400), jokers("j_burglar"));
        assertThat(run.state.handsLeft).isEqualTo(std.hands() + 3);
        assertThat(run.state.discardsLeft).isZero();
    }

    @Test
    void troubadourTradesAHandForHandSize() {
        Run run = new Run(std, "T", stoneDeck(400), jokers("j_troubadour"));
        assertThat(run.state.handsLeft).isEqualTo(std.hands() - 1);
        assertThat(run.state.handSize).isEqualTo(std.handSize() + 2);
    }

    @Test
    void hologramGainsWhenMarbleAddsACard() {
        // Marble adds a Stone card at blind select -> CARD_ADDED -> Hologram gains x0.25.
        Run run = new Run(std, "H", stoneDeck(400), jokers("j_hologram", "j_marble"));
        var holo = run.state.jokers().get(0);
        assertThat(((Number) run.state.jokerState(holo).get("x")).doubleValue()).isEqualTo(0.25);
    }

    @Test
    void sellingAJokerRefundsMoneyAndGrowsCampfire() {
        // Campfire + a Joker to sell ($2 -> sells for $1; Campfire gains x0.25).
        Run run = new Run(std, "S", stoneDeck(400), jokers("j_campfire", "j_joker"));
        int money = run.state.money;
        String err = run.sellJoker(1); // sell the plain Joker
        assertThat(err).isNull();
        assertThat(run.state.jokers()).hasSize(1);
        assertThat(run.state.money).isGreaterThan(money);
        var campfire = run.state.jokers().get(0);
        assertThat(((Number) run.state.jokerState(campfire).get("x")).doubleValue()).isEqualTo(0.25);
    }

    @Test
    void oopsAllSixesDoublesTheProbabilityNumerator() {
        Run with = new Run(std, "O", stoneDeck(400), jokers("j_oops"));
        assertThat(with.state.probabilityNumerator).isEqualTo(2);
        Run without = new Run(std, "N", stoneDeck(400), jokers("j_joker"));
        assertThat(without.state.probabilityNumerator).isEqualTo(1);
    }

    @Test
    void modsWithoutJokersAreUnchanged() {
        Run run = new Run(std, "N", stoneDeck(400), jokers("j_joker"));
        assertThat(run.state.handSize).isEqualTo(std.handSize());
        assertThat(run.state.discardsLeft).isEqualTo(std.discards());
    }
}
