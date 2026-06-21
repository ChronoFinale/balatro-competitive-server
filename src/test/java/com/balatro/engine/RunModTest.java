package com.balatro.engine;

import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.Run;
import com.balatro.engine.state.Ruleset;
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
    void ceremonialDaggerEatsRightNeighbourForMult() {
        Run run = new Run(std, "CD", stoneDeck(400), jokers("j_ceremonial", "j_joker"));
        assertThat(run.state.jokers()).hasSize(1); // the Joker to the right was consumed
        var dagger = run.state.jokers().get(0);
        assertThat(((Number) run.state.jokerState(dagger).getOrDefault("mult", 0)).intValue())
                .isGreaterThan(0); // gained mult from its sell value
    }

    @Test
    void madnessDestroysAJokerAndGainsXMult() {
        Run run = new Run(std, "MD", stoneDeck(400), jokers("j_madness", "j_joker"));
        assertThat(run.state.jokers()).hasSize(1); // a random other joker was destroyed
        var madness = run.state.jokers().get(0);
        assertThat(madness.key()).isEqualTo("j_madness");
        assertThat(((Number) run.state.jokerState(madness).getOrDefault("xm", 0.0)).doubleValue())
                .isEqualTo(0.5);
    }

    @Test
    void turtleBeanAddsFourHandSizeWhenFreshlyAcquired() {
        // BMP 0.4.2: +4 (vanilla +5). Acquired at run start (round 0), so it hasn't decayed yet.
        Run run = new Run(std, "TB", stoneDeck(400), jokers("j_turtle_bean"));
        assertThat(run.state.handSize).isEqualTo(std.handSize() + 4);
    }

    @Test
    void skippingABlindAdvancesAndCounts() {
        Run run = new Run(std, "SK", stoneDeck(400), jokers());
        assertThat(run.blind).isEqualTo(com.balatro.engine.game.Blinds.BlindType.SMALL);
        assertThat(run.skipBlind()).isNull();
        assertThat(run.blind).isEqualTo(com.balatro.engine.game.Blinds.BlindType.BIG);
        assertThat(run.state.blindsSkipped).isEqualTo(1);
        run.skipBlind(); // Big -> Boss
        assertThat(run.blind).isEqualTo(com.balatro.engine.game.Blinds.BlindType.BOSS);
        assertThat(run.skipBlind()).isNotNull(); // can't skip a boss
        assertThat(run.state.blindsSkipped).isEqualTo(2);
    }

    @Test
    void dietColaMakesADoubleTagThatDoublesTheNextSkipReward() {
        Run run = new Run(std, "DC", stoneDeck(400), jokers("j_diet_cola"));
        assertThat(run.sellJoker(0)).isNull();          // sell -> free Double Tag
        assertThat(run.state.tags).contains("tag_double");
        run.state.offeredTag = "tag_investment";        // force a known (held) tag to be offered on skip
        assertThat(run.skipBlind()).isNull();           // skip -> claim it, doubled by the Double Tag
        // Double Tag duplicated the claimed tag: two Investment tags now held.
        assertThat(run.state.tags.stream().filter(t -> t.equals("tag_investment")).count()).isEqualTo(2);
        assertThat(run.state.tags).doesNotContain("tag_double"); // consumed
    }

    @Test
    void skipOffGivesHandsAndDiscardsPerExtraSkip() {
        Run run = new Run(std, "SO", stoneDeck(400), jokers("j_skip_off"));
        run.skipBlind(); // 1 skip vs Nemesis 0 -> Big blind gets +1 hand and +1 discard
        assertThat(run.state.handsLeft).isEqualTo(std.hands() + 1);
        assertThat(run.state.discardsLeft).isEqualTo(std.discards() + 1);
    }

    @Test
    void pizzaGrantsTemporaryDiscardsForTheNextBlinds() {
        Run run = new Run(std, "PZ", stoneDeck(400), jokers());
        run.grantPizzaDiscards(2, 3); // +2 discards for the next 3 blinds
        run.skipBlind(); // advance to Big -> the bonus applies
        assertThat(run.state.discardsLeft).isEqualTo(std.discards() + 2);
    }

    @Test
    void modsWithoutJokersAreUnchanged() {
        Run run = new Run(std, "N", stoneDeck(400), jokers("j_joker"));
        assertThat(run.state.handSize).isEqualTo(std.handSize());
        assertThat(run.state.discardsLeft).isEqualTo(std.discards());
    }
}
