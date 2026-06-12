package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.jokers;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.rng.QueueSet;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.rng.RngContext;
import com.balatromp.engine.rng.RngSource;
import com.balatromp.engine.rng.RngSources;
import com.balatromp.engine.state.Deck;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The composable RNG model: {@link RngSource} properties resolve to the right keyed stream, and the
 * COMPOSITION selection delivers the two variance-reduction guarantees BMP's "The Order" exists for —
 * an order-independent shuffle whose draws barely move when a deck changes by one card, and a
 * "random" pick that depends on the <i>set</i> held, not its left-to-right arrangement.
 */
class RngSourceTest {

    private QueueSet queues() {
        return new QueueSet(new RandomStreams("VARIANCE"));
    }

    private List<Card> distinctDeck() {
        List<Card> d = new ArrayList<>();
        for (Suit s : Suit.values()) {
            for (Rank r : Rank.values()) {
                d.add(new Card(r, s)); // 52 distinct (suit,rank) identities
            }
        }
        return d;
    }

    private List<String> ids(List<Card> cards) {
        return cards.stream().map(Deck.CARD_GROUP).toList();
    }

    // --- resolve(): the single key-construction site ----------------------------------

    @Test
    void resolveStripsAnteForGameLongSourcesUnderTheOrder() {
        assertThat(queues().resolve(RngSources.VOUCHERS, RngContext.of(5, false, true)))
                .isEqualTo("vouchers");
    }

    @Test
    void resolveFallsBackToPerAnteWhenTheOrderIsOff() {
        assertThat(queues().resolve(RngSources.VOUCHERS, RngContext.of(5, false, false)))
                .isEqualTo("vouchers:5");
    }

    @Test
    void resolveKeysTheDealByAnteAndBlind() {
        assertThat(queues().resolve(RngSources.DEAL, new RngContext(2, "BIG", false, true)))
                .isEqualTo("deal:2:BIG");
    }

    @Test
    void pvpPerHandSourceGetsThePvpPrefixOnlyInsideAPvpBlind() {
        QueueSet qs = queues();
        assertThat(qs.resolve(RngSources.LUCKY_MULT, RngContext.of(3, false, true)))
                .isEqualTo("lucky_mult");
        assertThat(qs.resolve(RngSources.LUCKY_MULT, RngContext.of(3, true, true)))
                .as("a PvP blind routes to the per-hand reset queue").isEqualTo("pvp:3:lucky_mult");
    }

    @Test
    void chanceJokerProbResolvesToItsPvpVariant() {
        assertThat(queues().resolve(RngSources.PROB.sub("j_bloodstone"), RngContext.of(4, true, true)))
                .isEqualTo("pvp:4:prob:j_bloodstone");
    }

    @Test
    void contextFreeQueueRejectsAScopedOrPvpSource() {
        QueueSet qs = queues();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> qs.queue(RngSources.LUCKY_MULT, com.balatromp.engine.rng.Rng::nextDouble));
    }

    // --- COMPOSITION shuffle: low sensitivity -----------------------------------------

    @Test
    void equalDecksShuffleIdentically() {
        RngContext ctx = new RngContext(1, "SMALL", false, true);
        List<Card> a = distinctDeck();
        List<Card> b = distinctDeck();
        new QueueSet(new RandomStreams("SEED")).shuffle(a, RngSources.DEAL, ctx, Deck.CARD_GROUP, Deck.CARD_QUALITY);
        new QueueSet(new RandomStreams("SEED")).shuffle(b, RngSources.DEAL, ctx, Deck.CARD_GROUP, Deck.CARD_QUALITY);
        assertThat(ids(a)).isEqualTo(ids(b)); // two players, same seed + composition -> same deal
    }

    @Test
    void removingOneCardLeavesEveryOtherCardsRelativeOrderIntact() {
        RngContext ctx = new RngContext(1, "SMALL", false, true);

        List<Card> full = distinctDeck();
        queues().shuffle(full, RngSources.DEAL, ctx, Deck.CARD_GROUP, Deck.CARD_QUALITY);

        // Drop one identity, reshuffle the rest with the same source/seed.
        String dropped = Deck.CARD_GROUP.apply(full.get(10));
        List<Card> minus = distinctDeck();
        minus.removeIf(c -> Deck.CARD_GROUP.apply(c).equals(dropped));
        queues().shuffle(minus, RngSources.DEAL, ctx, Deck.CARD_GROUP, Deck.CARD_QUALITY);

        // The shuffled order of the remaining 51, with the dropped card filtered out of the full order,
        // must match exactly — a one-card change perturbs only that card's slot (low variance).
        List<String> fullMinusDropped = new ArrayList<>(ids(full));
        fullMinusDropped.remove(dropped);
        assertThat(ids(minus)).isEqualTo(fullMinusDropped);
    }

    // --- COMPOSITION pick: identity, not position -------------------------------------

    @Test
    void aRandomPickIgnoresListOrder() {
        QueueSet qs = queues();
        RngContext ctx = RngContext.of(1, false, true);
        RngSource src = RngSource.of("pick_test").composition();

        List<Joker> order1 = jokers("j_joker", "j_bull", "j_blueprint", "j_loyalty_card");
        List<Joker> order2 = new ArrayList<>(order1);
        java.util.Collections.reverse(order2);

        Joker p1 = qs.pick(order1, src, ctx, Joker::key, (a, b) -> 0);
        Joker p2 = qs.pick(order2, src, ctx, Joker::key, (a, b) -> 0);
        assertThat(p1.key()).isEqualTo(p2.key()); // same set -> same target regardless of arrangement
    }

    @Test
    void aRandomPickReturnsAMemberOfTheList() {
        List<Joker> js = jokers("j_joker", "j_bull", "j_blueprint");
        Joker picked = queues().pick(js, RngSource.of("p").composition(),
                RngContext.of(1, false, true), Joker::key, (a, b) -> 0);
        assertThat(js).extracting(Joker::key).contains(picked.key());
    }
}
