package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Run;
import com.balatromp.engine.game.TagCatalog;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Skip tags: each Small/Big skip offers a deterministic tag (Ante-1 lockouts respected),
 * claimed into the inventory; IMMEDIATE tags resolve at once, others at their trigger
 * (ON_SHOP / after a boss / next blind).
 */
class TagSystemTest {

    private static final Ruleset STD = Ruleset.standard();
    private static final Intent.PlayHand FIVE = new Intent.PlayHand(List.of(0, 1, 2, 3, 4));

    private Run run(String seed) {
        return new Run(STD, seed, stoneDeck(400),
                jokers("j_joker", "j_joker", "j_joker", "j_joker", "j_joker"));
    }

    @Test
    void aSkippableBlindOffersAnAnte1LegalTag() {
        Run run = run("TAGS");
        assertThat(run.state.offeredTag).isNotNull();
        assertThat(TagCatalog.get(run.state.offeredTag).ante1())
                .as("ante 1 only offers ante-1-legal tags").isTrue();
    }

    @Test
    void offeredTagIsDeterministicForTheSameSeed() {
        assertThat(run("DUEL").state.offeredTag).isEqualTo(run("DUEL").state.offeredTag);
    }

    @Test
    void skippingClaimsTheOfferedHeldTag() {
        Run run = run("TAGS");
        run.state.offeredTag = "tag_voucher"; // an ON_SHOP (held) tag
        assertThat(run.skipBlind()).isNull();
        assertThat(run.state.tags).contains("tag_voucher");
    }

    @Test
    void economyTagDoublesMoneyImmediately() {
        Run run = run("ECON");
        run.state.money = 10;
        run.state.offeredTag = "tag_economy";
        run.skipBlind();
        assertThat(run.state.money).isEqualTo(20); // +min(10, 40)
    }

    @Test
    void juggleTagAddsHandSizeAtTheNextBlind() {
        Run run = run("JUG");
        run.play(FIVE); // win Small -> shop
        int base = run.state.handSize;
        run.state.tags.add("tag_juggle");
        run.proceed(); // -> next blind start applies Juggle
        assertThat(run.state.handSize).isEqualTo(base + 3);
    }

    @Test
    void investmentTagPaysAfterBeatingABoss() {
        Run run = run("INV");
        run.play(FIVE);                 // Small -> shop
        run.proceed(); run.play(FIVE);  // Big -> shop
        run.proceed();                  // Boss select
        run.state.tags.add("tag_investment");
        int money = run.state.money;
        run.play(FIVE);                 // beat the Boss
        assertThat(run.state.tags).doesNotContain("tag_investment"); // consumed
        assertThat(run.state.money).isGreaterThanOrEqualTo(money + 25);
    }

    @Test
    void rareTagAddsAFreeRareJokerToTheNextShop() {
        Run run = run("RARE");
        run.state.tags.add("tag_rare");
        run.play(FIVE); // win Small -> shop opens, ON_SHOP tags resolve
        var free = run.shop.items().stream()
                .filter(it -> it.kind() == com.balatromp.engine.game.Shop.Kind.JOKER)
                .filter(it -> it.cost() == 0 && "Rare".equals(it.rarity()))
                .findFirst();
        assertThat(free).as("a free Rare joker was added").isPresent();
        assertThat(run.state.tags).doesNotContain("tag_rare"); // consumed
    }

    @Test
    void charmTagAddsAFreeMegaArcanaPack() {
        Run run = run("CHARM");
        run.state.tags.add("tag_charm");
        run.play(FIVE); // win Small -> shop
        assertThat(run.view().packs()).hasSize(3); // 2 base packs + the free Arcana pack
        assertThat(run.state.tags).doesNotContain("tag_charm");
    }

    @Test
    void couponTagMakesShopCardsFree() {
        Run run = run("COUP");
        run.state.tags.add("tag_coupon");
        run.play(FIVE); // win Small -> shop with Coupon active
        run.state.money = 0;        // can't afford anything normally
        run.state.jokerSlots = 10;  // room for whatever slot 0 is
        run.state.consumableSlots = 10;
        assertThat(run.buyShopItem(0)).isNull(); // but Coupon makes it free
    }

    @Test
    void voucherTagAddsASecondVoucherFromTheSameQueue() {
        Run run = run("VOUCH");
        run.state.tags.add("tag_voucher");
        run.play(FIVE); // win Small -> shop opens, ON_SHOP tags resolve
        assertThat(run.shop.vouchers()).as("per-ante voucher + the tag voucher").hasSize(2);
        assertThat(run.shop.vouchers().get(0)).isNotEqualTo(run.shop.vouchers().get(1)); // distinct
        assertThat(run.state.tags).doesNotContain("tag_voucher"); // consumed
    }

    @Test
    void voucherTagVoucherSurvivesARerollAndIsBuyable() {
        Run run = run("VOUCH2");
        run.state.tags.add("tag_voucher");
        run.play(FIVE); // win Small -> shop (2 vouchers)
        run.state.money = 100; // afford reroll + the voucher
        run.reroll();
        assertThat(run.shop.vouchers()).as("tag voucher persists across reroll").hasSize(2);
        String tagVoucher = run.shop.vouchers().get(1);
        assertThat(run.buyVoucher(1)).isNull();               // buy the extra (tag) voucher
        assertThat(run.state.vouchers).contains(tagVoucher);
        assertThat(run.shop.vouchers()).hasSize(1);           // only the per-ante one remains
    }

    @Test
    void multiplayerOmitsBossRerollTagFromOffers() {
        assertThat(TagCatalog.offerable(2, true)).doesNotContain("tag_boss");
        assertThat(TagCatalog.offerable(2, false)).contains("tag_boss");
    }
}
