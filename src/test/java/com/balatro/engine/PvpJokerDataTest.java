package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Run;
import com.balatro.engine.state.Ruleset;
import org.junit.jupiter.api.Test;

/**
 * The two PvP/Match jokers, now expressed as data, exercised through the Run hooks the Match calls
 * ({@code pvpReached} / {@code pvpEnded}). They prove the point that "the condition needs the server" does
 * not mean "it can't be data": the Match supplies the cross-player context (arrival order, the opponent
 * run) on the EvaluationContext, exactly as the server supplies RNG to a {@code chance()} condition, and the
 * joker's declared rule does the rest.
 */
class PvpJokerDataTest {

    private final Ruleset std = Ruleset.standard();

    @Test
    void speedrunCreatesASpectralOnlyWhenReachingPvpFirst() {
        Run first = new Run(std, "SPD", stoneDeck(400), jokers("j_speedrun"));
        first.pvpReached(true); // arrived before the Nemesis
        assertThat(first.state.consumables).as("Speedrun first -> a Spectral").hasSize(1);

        Run second = new Run(std, "SPD", stoneDeck(400), jokers("j_speedrun"));
        second.pvpReached(false); // Nemesis was already there
        assertThat(second.state.consumables).as("Speedrun not first -> nothing").isEmpty();
    }

    @Test
    void pizzaConsumesItselfAndGrantsDiscardsToBothPlayers() {
        Run me = new Run(std, "PZA", stoneDeck(400), jokers("j_pizza"));
        Run opp = new Run(std, "PZB", stoneDeck(400), jokers());

        me.pvpEnded(opp.state); // PvP resolved, Nemesis = opp

        assertThat(me.state.pizzaDiscardBonus).as("+1 discard to me").isEqualTo(1);
        assertThat(opp.state.pizzaDiscardBonus).as("+2 discards to the Nemesis").isEqualTo(2);
        assertThat(me.state.jokers()).as("Pizza is consumed").noneMatch(j -> j.key().equals("j_pizza"));
    }

    @Test
    void pizzaIsInertWithoutAPvpEnd() {
        Run me = new Run(std, "PZC", stoneDeck(400), jokers("j_pizza"));
        assertThat(me.state.pizzaDiscardBonus).isZero();           // not triggered yet
        assertThat(me.state.jokers()).anyMatch(j -> j.key().equals("j_pizza")); // still held
    }
}
