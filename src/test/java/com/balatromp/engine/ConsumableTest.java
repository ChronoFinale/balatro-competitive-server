package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.state.Ruleset;
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
        assertThat(run.useConsumable(0, new long[]{a.uid, b.uid})).isNull();
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
        assertThat(run.useConsumable(0, new long[]{a.uid, b.uid})).isNull();
        assertThat(run.state.hand).doesNotContain(a, b);
        assertThat(run.state.hand).hasSize(before - 2);
    }

    @Test
    void talismanAddsAGoldSealToASelectedCard() {
        Run run = freshRun();
        Card a = run.state.hand.get(0);
        run.state.consumables.add("c_talisman");
        assertThat(run.useConsumable(0, new long[]{a.uid})).isNull();
        assertThat(a.seal).isEqualTo(com.balatromp.engine.card.Seal.GOLD);
    }

    @Test
    void blackHoleLevelsUpEveryHand() {
        Run run = freshRun();
        int pairBefore = run.state.handLevel(com.balatromp.engine.hand.HandType.PAIR);
        int flushBefore = run.state.handLevel(com.balatromp.engine.hand.HandType.FLUSH);
        run.state.consumables.add("c_black_hole");
        assertThat(run.useConsumable(0)).isNull(); // no targets
        assertThat(run.state.handLevel(com.balatromp.engine.hand.HandType.PAIR)).isEqualTo(pairBefore + 1);
        assertThat(run.state.handLevel(com.balatromp.engine.hand.HandType.FLUSH)).isEqualTo(flushBefore + 1);
    }

    @Test
    void ectoplasmAddsNegativeToARandomJokerAndDropsHandSize() {
        Run run = new Run(Ruleset.standard(), "ECTO",
                com.balatromp.engine.TestSupport.heartsKings(50),
                com.balatromp.engine.TestSupport.jokers("j_joker"));
        int handBefore = run.state.handSize;
        run.state.consumables.add("c_ectoplasm");
        assertThat(run.useConsumable(0)).isNull(); // no card targets
        assertThat(run.state.jokerEdition(run.state.jokers().get(0)))
                .isEqualTo(com.balatromp.engine.card.Edition.NEGATIVE);
        assertThat(run.state.jokerSlots).isEqualTo(6); // Negative granted a slot
        assertThat(run.state.handSize).isEqualTo(handBefore - 1);
    }

    @Test
    void hexAddsPolychromeAndDestroysOtherJokers() {
        Run run = new Run(Ruleset.standard(), "HEX",
                com.balatromp.engine.TestSupport.heartsKings(50),
                com.balatromp.engine.TestSupport.jokers("j_joker", "j_greedy_joker", "j_lusty_joker"));
        run.state.consumables.add("c_hex");
        assertThat(run.useConsumable(0)).isNull();
        assertThat(run.state.jokers()).hasSize(1); // only the chosen joker survives
        assertThat(run.state.jokerEdition(run.state.jokers().get(0)))
                .isEqualTo(com.balatromp.engine.card.Edition.POLYCHROME);
    }

    @Test
    void wheelOfFortuneEitherGrantsAFoilHoloPolyOrDoesNothing() {
        Run run = new Run(Ruleset.standard(), "WHEEL",
                com.balatromp.engine.TestSupport.heartsKings(50),
                com.balatromp.engine.TestSupport.jokers("j_joker"));
        run.state.consumables.add("c_wheel_of_fortune");
        assertThat(run.useConsumable(0)).isNull();
        var ed = run.state.jokerEdition(run.state.jokers().get(0));
        // 1-in-4: the joker ends up either un-editioned or with one of Foil/Holo/Poly (never Negative).
        assertThat(ed).isIn(com.balatromp.engine.card.Edition.NONE,
                com.balatromp.engine.card.Edition.FOIL,
                com.balatromp.engine.card.Edition.HOLOGRAPHIC,
                com.balatromp.engine.card.Edition.POLYCHROME);
    }

    @Test
    void tooManyTargetsRejected() {
        Run run = freshRun();
        long[] three = {run.state.hand.get(0).uid, run.state.hand.get(1).uid, run.state.hand.get(2).uid};
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
        assertThat(run.useConsumable(0, new long[]{a.uid, b.uid})).isNull();
        assertThat(a.rank).isEqualTo(Rank.ACE);
        assertThat(b.rank).isEqualTo(Rank.TWO); // same card objects, identity (uid) preserved
    }

    @Test
    void theSunConvertsSelectedCardsToHearts() {
        Run run = freshRun();
        Card a = run.state.hand.get(0);
        a.suit = Suit.SPADES;
        run.state.consumables.add("c_sun");
        assertThat(run.useConsumable(0, new long[]{a.uid})).isNull();
        assertThat(a.suit).isEqualTo(Suit.HEARTS);
    }

    @Test
    void incantationCreatesBonusCards() {
        Run run = freshRun();
        int before = run.state.hand.size();
        run.state.consumables.add("c_incantation");
        assertThat(run.useConsumable(0, new long[0])).isNull();
        assertThat(run.state.hand).hasSize(before + 4);
        long bonus = run.state.hand.stream().filter(c -> c.enhancement == Enhancement.BONUS).count();
        assertThat(bonus).isGreaterThanOrEqualTo(4);
        // created cards have fresh unique ids
        assertThat(run.state.hand.stream().map(c -> c.uid).distinct().count())
                .isEqualTo(run.state.hand.size());
    }
}
