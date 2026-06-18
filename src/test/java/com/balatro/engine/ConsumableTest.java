package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Suit;
import com.balatro.engine.game.Run;
import com.balatro.engine.state.Ruleset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tarots/Spectrals act on cards by unique id: enhance selected cards, destroy
 * selected cards (removed from the deck), and create new cards. Targeting uses
 * the stable card id, not positional indices.
 */
class ConsumableTest {

    private Run freshRun() {
        return new Run(Ruleset.standard(), "CONSUME"); // starts in ante-1 small with a drawn hand
    }

    @Test
    void magicianEnhancesSelectedCardsByUid() {
        Run run = freshRun();
        Card a = run.state.hand.get(0);
        Card b = run.state.hand.get(1);
        run.state.consumables.add("c_magician");
        assertThat(run.useConsumable(0, new UUID[]{a.uid, b.uid})).isNull();
        assertThat(a.enhancement).isEqualTo(Enhancement.LUCKY);
        assertThat(b.enhancement).isEqualTo(Enhancement.LUCKY);
        assertThat(run.state.consumables).isEmpty(); // consumed
    }

    @Test
    void hangedManDestroysSelectedCards() {
        Run run = freshRun();
        int before = run.state.hand.size();
        Card a = run.state.hand.get(0);
        Card b = run.state.hand.get(1);
        run.state.consumables.add("c_hanged_man");
        assertThat(run.useConsumable(0, new UUID[]{a.uid, b.uid})).isNull();
        assertThat(run.state.hand).doesNotContain(a, b);
        assertThat(run.state.hand).hasSize(before - 2);
    }

    @Test
    void talismanAddsAGoldSealToASelectedCard() {
        Run run = freshRun();
        Card a = run.state.hand.get(0);
        run.state.consumables.add("c_talisman");
        assertThat(run.useConsumable(0, new UUID[]{a.uid})).isNull();
        assertThat(a.seal).isEqualTo(com.balatro.engine.card.Seal.GOLD);
    }

    @Test
    void blackHoleLevelsUpEveryHand() {
        Run run = freshRun();
        int pairBefore = run.state.handLevel(com.balatro.engine.hand.HandType.PAIR);
        int flushBefore = run.state.handLevel(com.balatro.engine.hand.HandType.FLUSH);
        run.state.consumables.add("c_black_hole");
        assertThat(run.useConsumable(0)).isNull(); // no targets
        assertThat(run.state.handLevel(com.balatro.engine.hand.HandType.PAIR)).isEqualTo(pairBefore + 1);
        assertThat(run.state.handLevel(com.balatro.engine.hand.HandType.FLUSH)).isEqualTo(flushBefore + 1);
    }

    @Test
    void ectoplasmAddsNegativeToARandomJokerAndDropsHandSize() {
        Run run = new Run(Ruleset.standard(), "ECTO",
                com.balatro.engine.TestSupport.heartsKings(50),
                com.balatro.engine.TestSupport.jokers("j_joker"));
        int handBefore = run.state.handSize;
        run.state.consumables.add("c_ectoplasm");
        assertThat(run.useConsumable(0)).isNull(); // no card targets
        assertThat(run.state.jokerEdition(run.state.jokers().get(0)))
                .isEqualTo(com.balatro.engine.card.Edition.NEGATIVE);
        assertThat(run.state.jokerSlots).isEqualTo(6); // Negative granted a slot
        assertThat(run.state.handSize).isEqualTo(handBefore - 1);
    }

    @Test
    void theWraithSkipsRareJokersYouAlreadyOwn() {
        // Own every Rare but one; the Wraith must create the missing one, never a duplicate
        // (BMP marks owned jokers UNAVAILABLE in the pool and the draw advances past them).
        java.util.List<String> rares = com.balatro.engine.joker.JokerLibrary.keysByRarity("Rare");
        org.junit.jupiter.api.Assumptions.assumeTrue(rares.size() >= 2);
        String missing = rares.get(rares.size() - 1);
        java.util.List<com.balatro.engine.joker.Joker> owned = new java.util.ArrayList<>();
        for (int i = 0; i < rares.size() - 1; i++) owned.add(com.balatro.engine.joker.JokerLibrary.create(rares.get(i)));
        Run run = new Run(Ruleset.standard(), "WRAITH", com.balatro.engine.TestSupport.heartsKings(50), owned);
        run.state.jokerSlots = 50; // room for the create
        run.state.consumables.add("c_wraith");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.jokers()).extracting(com.balatro.engine.joker.Joker::key)
                .as("created the only un-owned Rare, not a duplicate").contains(missing);
        assertThat(run.state.jokers()).hasSize(rares.size()); // exactly one new joker
    }

    @Test
    void multiplayerSoulNeverCreatesABannedLegendary() {
        Ruleset mp = new Ruleset("MP", 4, 4, 3, 8, 1.0, 8,
                new int[]{300, 800, 2000, 5000, 11000, 20000, 35000, 50000}, null, "multiplayer");
        java.util.List<String> legends = com.balatro.engine.joker.JokerLibrary.keysByRarity("Legendary")
                .stream().filter(k -> !com.balatro.engine.joker.JokerLibrary.MP_BANNED.contains(k)).toList();
        org.junit.jupiter.api.Assumptions.assumeTrue(legends.size() >= 2);
        String missing = legends.get(legends.size() - 1);
        java.util.List<com.balatro.engine.joker.Joker> owned = new java.util.ArrayList<>();
        for (int i = 0; i < legends.size() - 1; i++) owned.add(com.balatro.engine.joker.JokerLibrary.create(legends.get(i)));
        Run run = new Run(mp, "SOULMP", com.balatro.engine.TestSupport.heartsKings(50), owned);
        run.state.jokerSlots = 50;
        run.state.consumables.add("c_the_soul");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.jokers()).extracting(com.balatro.engine.joker.Joker::key)
                .contains(missing)            // created the only un-owned legal Legendary (skip-owned)
                .doesNotContain("j_chicot");  // never the MP-banned one (pool filter)
    }

    @Test
    void spectralPoolExcludesTheSoulAndBlackHole() {
        assertThat(com.balatro.engine.consumable.TarotCatalog.spectralKeys())
                .doesNotContain("c_the_soul", "c_black_hole");
    }

    @Test
    void hexAddsPolychromeAndDestroysOtherJokers() {
        Run run = new Run(Ruleset.standard(), "HEX",
                com.balatro.engine.TestSupport.heartsKings(50),
                com.balatro.engine.TestSupport.jokers("j_joker", "j_greedy_joker", "j_lusty_joker"));
        run.state.consumables.add("c_hex");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.jokers()).hasSize(1); // only the chosen joker survives
        assertThat(run.state.jokerEdition(run.state.jokers().get(0)))
                .isEqualTo(com.balatro.engine.card.Edition.POLYCHROME);
    }

    @Test
    void wheelOfFortuneEitherGrantsAFoilHoloPolyOrDoesNothing() {
        Run run = new Run(Ruleset.standard(), "WHEEL",
                com.balatro.engine.TestSupport.heartsKings(50),
                com.balatro.engine.TestSupport.jokers("j_joker"));
        run.state.consumables.add("c_wheel_of_fortune");
        assertThat(run.useConsumable(0)).isNull();
        var ed = run.state.jokerEdition(run.state.jokers().get(0));
        // 1-in-4: the joker ends up either un-editioned or with one of Foil/Holo/Poly (never Negative).
        assertThat(ed).isIn(com.balatro.engine.card.Edition.NONE,
                com.balatro.engine.card.Edition.FOIL,
                com.balatro.engine.card.Edition.HOLOGRAPHIC,
                com.balatro.engine.card.Edition.POLYCHROME);
    }

    @Test
    void emperorCreatesTwoTarotsIntoFreedSlots() {
        Run run = freshRun();
        run.state.consumableSlots = 3;
        run.state.consumables.add("c_emperor");
        assertThat(run.useConsumable(0)).isNull();
        // Emperor is consumed; 2 new Tarots take its place (slot freed before creating).
        assertThat(run.state.consumables).hasSize(2);
        assertThat(run.state.consumables).allMatch(k ->
                com.balatro.engine.consumable.TarotCatalog.get(k).type()
                        == com.balatro.engine.consumable.ConsumableType.TAROT);
    }

    @Test
    void highPriestessCreatesTwoPlanets() {
        Run run = freshRun();
        run.state.consumableSlots = 3;
        run.state.consumables.add("c_high_priestess");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.consumables).hasSize(2);
        assertThat(run.state.consumables).allMatch(k ->
                com.balatro.engine.game.PlanetCatalog.get(k) != null);
    }

    @Test
    void judgementCreatesAJoker() {
        Run run = freshRun();
        int before = run.state.jokers().size();
        run.state.consumables.add("c_judgement");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.jokers()).hasSize(before + 1);
    }

    @Test
    void theSoulCreatesALegendaryJoker() {
        Run run = freshRun();
        run.state.consumables.add("c_the_soul");
        assertThat(run.useConsumable(0)).isNull();
        var made = run.state.jokers().get(run.state.jokers().size() - 1);
        assertThat(made.info().rarity()).isEqualTo("Legendary");
    }

    @Test
    void wraithCreatesARareJokerAndZeroesMoney() {
        Run run = freshRun();
        run.state.money = 17;
        run.state.consumables.add("c_wraith");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.money).isEqualTo(0);
        var made = run.state.jokers().get(run.state.jokers().size() - 1);
        assertThat(made.info().rarity()).isEqualTo("Rare");
    }

    @Test
    void hermitDoublesMoneyCappedAtTwenty() {
        Run run = freshRun();
        run.state.money = 8;
        run.state.consumables.add("c_hermit");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.money).isEqualTo(16); // +min(8,20)
        run.state.money = 50;
        run.state.consumables.add("c_hermit");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.money).isEqualTo(70); // +min(50,20) cap
    }

    @Test
    void temperanceGivesTotalJokerSellValueCappedAtFifty() {
        Run run = new Run(Ruleset.standard(), "TEMP",
                com.balatro.engine.TestSupport.heartsKings(50),
                com.balatro.engine.TestSupport.jokers("j_joker", "j_joker")); // each cost 2 -> sell 1
        run.state.money = 0;
        run.state.consumables.add("c_temperance");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.money).isEqualTo(2); // 1 + 1 sell value, under the $50 cap
    }

    @Test
    void immolateDestroysFiveCardsAndGainsTwenty() {
        Run run = freshRun();
        int before = run.state.hand.size();
        run.state.money = 5;
        run.state.consumables.add("c_immolate");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.hand).hasSize(before - 5);
        assertThat(run.state.money).isEqualTo(25);
    }

    @Test
    void familiarDestroysOneAndAddsThreeEnhancedFaceCards() {
        Run run = freshRun();
        int before = run.state.hand.size();
        run.state.consumables.add("c_familiar");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.hand).hasSize(before - 1 + 3);
        long enhancedFaces = run.state.hand.stream()
                .filter(c -> c.rank.isFace() && c.enhancement != Enhancement.NONE).count();
        assertThat(enhancedFaces).isGreaterThanOrEqualTo(3);
    }

    @Test
    void deathConvertsTheLeftSelectedCardIntoTheRight() {
        Run run = freshRun();
        Card left = run.state.hand.get(0);
        Card right = run.state.hand.get(1);
        right.rank = Rank.ACE;
        right.suit = Suit.SPADES;
        right.enhancement = Enhancement.GLASS;
        run.state.consumables.add("c_death");
        assertThat(run.useConsumable(0, new UUID[]{left.uid, right.uid})).isNull();
        assertThat(left.rank).isEqualTo(Rank.ACE);
        assertThat(left.suit).isEqualTo(Suit.SPADES);
        assertThat(left.enhancement).isEqualTo(Enhancement.GLASS);
    }

    @Test
    void sigilConvertsWholeHandToOneSuit() {
        Run run = freshRun();
        run.state.consumables.add("c_sigil");
        assertThat(run.useConsumable(0)).isNull();
        long distinctSuits = run.state.hand.stream().map(c -> c.suit).distinct().count();
        assertThat(distinctSuits).isEqualTo(1);
    }

    @Test
    void ouijaConvertsWholeHandToOneRankAndDropsHandSize() {
        Run run = freshRun();
        int handSizeBefore = run.state.handSize;
        run.state.consumables.add("c_ouija");
        assertThat(run.useConsumable(0)).isNull();
        long distinctRanks = run.state.hand.stream().map(c -> c.rank).distinct().count();
        assertThat(distinctRanks).isEqualTo(1);
        assertThat(run.state.handSize).isEqualTo(handSizeBefore - 1);
    }

    @Test
    void cryptidCreatesTwoCopiesOfASelectedCard() {
        Run run = freshRun();
        Card src = run.state.hand.get(0);
        src.rank = Rank.ACE;
        src.suit = Suit.HEARTS;
        int before = run.state.hand.size();
        run.state.consumables.add("c_cryptid");
        assertThat(run.useConsumable(0, new UUID[]{src.uid})).isNull();
        assertThat(run.state.hand).hasSize(before + 2);
        long aceHearts = run.state.hand.stream()
                .filter(c -> c.rank == Rank.ACE && c.suit == Suit.HEARTS).count();
        assertThat(aceHearts).isGreaterThanOrEqualTo(3); // original + 2 copies
        // copies carry fresh ids (no duplicate uids)
        assertThat(run.state.hand.stream().map(c -> c.uid).distinct().count())
                .isEqualTo(run.state.hand.size());
    }

    @Test
    void ankhCopiesAJokerAndDestroysTheOthers() {
        Run run = new Run(Ruleset.standard(), "ANKH",
                com.balatro.engine.TestSupport.heartsKings(50),
                com.balatro.engine.TestSupport.jokers("j_joker", "j_greedy_joker", "j_lusty_joker"));
        run.state.consumables.add("c_ankh");
        assertThat(run.useConsumable(0)).isNull();
        // The chosen joker survives plus its copy = 2, all of the same key.
        assertThat(run.state.jokers()).hasSize(2);
        assertThat(run.state.jokers().get(0).key()).isEqualTo(run.state.jokers().get(1).key());
    }

    @Test
    void foolCopiesTheLastTarotOrPlanetUsed() {
        Run run = freshRun();
        run.state.consumableSlots = 4;
        // Use a Planet first so it becomes the "last used".
        run.state.consumables.add("c_pluto"); // a Planet
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.lastTarotPlanetUsed).isEqualTo("c_pluto");
        run.state.consumables.add("c_fool");
        assertThat(run.useConsumable(run.state.consumables.size() - 1)).isNull();
        assertThat(run.state.consumables).contains("c_pluto"); // Fool re-created the Planet
    }

    @Test
    void tooManyTargetsRejected() {
        Run run = freshRun();
        UUID[] three = {run.state.hand.get(0).uid, run.state.hand.get(1).uid, run.state.hand.get(2).uid};
        run.state.consumables.add("c_magician"); // max 2
        assertThat(run.useConsumable(0, three)).isEqualTo("too many targets");
        assertThat(run.state.consumables).hasSize(1); // not consumed on rejection
    }

    @Test
    void strengthIncreasesRankAndWrapsAce() {
        Run run = freshRun();
        Card a = run.state.hand.get(0);
        a.rank = Rank.KING;        // King -> Ace
        Card b = run.state.hand.get(1);
        b.rank = Rank.ACE;         // Ace wraps -> Two
        run.state.consumables.add("c_strength");
        assertThat(run.useConsumable(0, new UUID[]{a.uid, b.uid})).isNull();
        assertThat(a.rank).isEqualTo(Rank.ACE);
        assertThat(b.rank).isEqualTo(Rank.TWO); // same card objects, identity (uid) preserved
    }

    @Test
    void theSunConvertsSelectedCardsToHearts() {
        Run run = freshRun();
        Card a = run.state.hand.get(0);
        a.suit = Suit.SPADES;
        run.state.consumables.add("c_sun");
        assertThat(run.useConsumable(0, new UUID[]{a.uid})).isNull();
        assertThat(a.suit).isEqualTo(Suit.HEARTS);
    }

    @Test
    void incantationCreatesBonusCards() {
        Run run = freshRun();
        int before = run.state.hand.size();
        run.state.consumables.add("c_incantation");
        assertThat(run.useConsumable(0, new UUID[0])).isNull();
        assertThat(run.state.hand).hasSize(before + 4);
        long bonus = run.state.hand.stream().filter(c -> c.enhancement == Enhancement.BONUS).count();
        assertThat(bonus).isGreaterThanOrEqualTo(4);
        // created cards have fresh unique ids
        assertThat(run.state.hand.stream().map(c -> c.uid).distinct().count())
                .isEqualTo(run.state.hand.size());
    }
}
