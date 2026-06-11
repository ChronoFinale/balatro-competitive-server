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
    void multiplayerOmitsBossRerollTagFromOffers() {
        assertThat(TagCatalog.offerable(2, true)).doesNotContain("tag_boss");
        assertThat(TagCatalog.offerable(2, false)).contains("tag_boss");
    }
}
