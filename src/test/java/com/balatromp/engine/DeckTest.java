package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Suit;
import com.balatromp.engine.game.DeckCatalog;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.state.Ruleset;
import com.balatromp.engine.state.Stake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Deck variants apply their starting / per-blind modifiers. */
class DeckTest {

    private final int[] amounts = Ruleset.standard().blindBaseAmounts();

    private Ruleset withDeck(String deck) {
        return new Ruleset("D", 4, 4, 3, 8, 1.0, 8, amounts, null, "default", deck);
    }

    @Test
    void yellowDeckStartsWithExtraMoney() {
        Run run = new Run(withDeck("d_yellow"), "Y", stoneDeck(400), jokers());
        assertThat(run.state.money).isEqualTo(4 + 10);
    }

    @Test
    void blueDeckGivesAnExtraHandEachBlind() {
        Run run = new Run(withDeck("d_blue"), "B", stoneDeck(400), jokers());
        assertThat(run.state.handsLeft).isEqualTo(5); // 4 + 1
    }

    @Test
    void blackDeckAddsAJokerSlotButRemovesAHand() {
        Run run = new Run(withDeck("d_black"), "K", stoneDeck(400), jokers());
        assertThat(run.state.jokerSlots).isEqualTo(6); // 5 + 1
        assertThat(run.state.handsLeft).isEqualTo(3); // 4 - 1
    }

    @Test
    void baseDeckIsUnmodified() {
        Run run = new Run(withDeck("d_base"), "0", stoneDeck(400), jokers());
        assertThat(run.state.money).isEqualTo(4);
        assertThat(run.state.handsLeft).isEqualTo(4);
        assertThat(run.state.jokerSlots).isEqualTo(5);
    }

    // --- picking a deck (and stake) at run creation, independent of the ruleset ---

    @Test
    void deckCanBeChosenPerRunIndependentOfTheRuleset() {
        // The standard ruleset uses d_base, but we select Blue at run creation.
        Run run = new Run(Ruleset.standard(), "B", Stake.WHITE, DeckCatalog.get("d_blue"));
        assertThat(run.state.handsLeft).isEqualTo(5);          // Blue deck: +1 hand
        assertThat(run.view().deck()).isEqualTo("Blue Deck");
    }

    @Test
    void deckAndStakeApplyTogether() {
        // Blue deck (+1 hand) AND Blue stake (-1 discard), chosen together.
        Run run = new Run(Ruleset.standard(), "BB", Stake.BLUE, DeckCatalog.get("d_blue"));
        assertThat(run.view().handsLeft()).isEqualTo(5);       // deck:  4 + 1
        assertThat(run.view().discardsLeft()).isEqualTo(2);    // stake: 3 - 1
        assertThat(run.view().deck()).isEqualTo("Blue Deck");
        assertThat(run.view().stake()).isEqualTo("Blue Stake");
    }

    // --- composition / hand-size decks (game.lua:636-642) ---

    @Test
    void paintedDeckBuffsHandSizeAndCutsAJokerSlot() {
        Run run = new Run(Ruleset.standard(), "P", Stake.WHITE, DeckCatalog.get("d_painted"));
        assertThat(run.view().handSize()).isEqualTo(10);  // 8 + 2
        assertThat(run.state.jokerSlots).isEqualTo(4);    // 5 - 1
    }

    @Test
    void abandonedDeckRemovesEveryFaceCard() {
        Run run = new Run(Ruleset.standard(), "A", Stake.WHITE, DeckCatalog.get("d_abandoned"));
        assertThat(run.state.deckComposition).hasSize(40);                       // 52 - 12 faces
        assertThat(run.state.deckComposition).noneMatch(c -> c.rank.isFace());
    }

    @Test
    void checkeredDeckIs26SpadesAnd26Hearts() {
        var comp = new Run(Ruleset.standard(), "C", Stake.WHITE, DeckCatalog.get("d_checkered"))
                .state.deckComposition;
        assertThat(comp).hasSize(52);
        assertThat(comp).noneMatch(c -> c.suit == Suit.CLUBS || c.suit == Suit.DIAMONDS);
        assertThat(comp.stream().filter(c -> c.suit == Suit.SPADES).count()).isEqualTo(26);
        assertThat(comp.stream().filter(c -> c.suit == Suit.HEARTS).count()).isEqualTo(26);
    }

    @Test
    void erraticDeckRandomizesButIsDeterministicPerSeed() {
        Run a = new Run(Ruleset.standard(), "ERR", Stake.WHITE, DeckCatalog.get("d_erratic"));
        Run b = new Run(Ruleset.standard(), "ERR", Stake.WHITE, DeckCatalog.get("d_erratic"));
        Run other = new Run(Ruleset.standard(), "OTHER", Stake.WHITE, DeckCatalog.get("d_erratic"));
        assertThat(a.state.deckComposition).hasSize(52);
        assertThat(idents(a)).isEqualTo(idents(b));        // same seed -> reproducible
        assertThat(idents(a)).isNotEqualTo(idents(other)); // different seed -> different deck
    }

    private static List<String> idents(Run run) {
        return run.state.deckComposition.stream().map(c -> c.rank + "-" + c.suit).toList();
    }

    // --- starting-grant decks (game.lua:633-638): begin with vouchers/consumables ---

    @Test
    void magicDeckGrantsCrystalBallAndTwoFools() {
        Run run = new Run(Ruleset.standard(), "M", Stake.WHITE, DeckCatalog.get("d_magic"));
        assertThat(run.state.vouchers).contains("v_crystal_ball");
        assertThat(run.state.consumableSlots).isEqualTo(3);   // 2 + Crystal Ball's +1
        assertThat(run.state.consumables).containsExactly("c_fool", "c_fool");
    }

    @Test
    void nebulaDeckGrantsTelescopeAndCutsAConsumableSlot() {
        Run run = new Run(Ruleset.standard(), "N", Stake.WHITE, DeckCatalog.get("d_nebula"));
        assertThat(run.state.vouchers).contains("v_telescope");
        assertThat(run.state.consumableSlots).isEqualTo(1);   // 2 - 1
    }

    @Test
    void zodiacDeckGrantsTheThreeMerchantVouchers() {
        Run run = new Run(Ruleset.standard(), "Z", Stake.WHITE, DeckCatalog.get("d_zodiac"));
        assertThat(run.state.vouchers).contains("v_tarot_merchant", "v_planet_merchant", "v_overstock");
    }

    @Test
    void plasmaDeckDoublesBlindRequirements() {
        Run run = new Run(Ruleset.standard(), "PL", Stake.WHITE, DeckCatalog.get("d_plasma"));
        assertThat(run.view().requirement()).isEqualTo(600L); // ante-1 Small 300 x2
    }
}
