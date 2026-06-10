package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.c;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.joker.def.DataJoker;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoreResult;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The data-driven built-in jokers: they register into the curated shop pool,
 * resolve through the normal create() path, and score with the real Balatro
 * numbers. Asserts deltas (with-joker minus without) so the tests are robust to
 * base-hand changes.
 */
class BuiltinJokersTest {

    private static final List<String> NEW_KEYS = List.of(
            "j_lusty_joker", "j_wrathful_joker", "j_gluttenous_joker", "j_jolly",
            "j_mystic_summit", "j_banner", "j_bull", "j_scary_face", "j_odd_todd", "j_square",
            "j_zany", "j_mad", "j_crazy", "j_droll",
            "j_wily", "j_clever", "j_devious", "j_crafty", "j_scholar");

    private ScoreResult score(List<Card> played, Consumer<RunState> cfg, String... jokerKeys) {
        RunState run = new RunState();
        cfg.accept(run);
        for (String k : jokerKeys) run.addJoker(JokerLibrary.create(k));
        return new ScoringEngine().score(played, List.of(), run, new RandomStreams("T"));
    }

    private static final Consumer<RunState> DEFAULTS = r -> { };

    @Test
    void allRegisteredIntoTheBuiltinShopPool() {
        for (String key : NEW_KEYS) {
            assertThat(JokerLibrary.builtinKeys()).contains(key);
            assertThat(JokerLibrary.create(key)).isInstanceOf(DataJoker.class);
        }
    }

    @Test
    void lustyAddsMultPerHeart() {
        // Flush of hearts -> all five score as Hearts -> Lusty +3 each = +15 Mult.
        List<Card> flush = List.of(c(Rank.TWO, Suit.HEARTS), c(Rank.FOUR, Suit.HEARTS),
                c(Rank.SIX, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.TEN, Suit.HEARTS));
        double delta = score(flush, DEFAULTS, "j_lusty_joker").mult() - score(flush, DEFAULTS).mult();
        assertThat(delta).isEqualTo(15.0);
    }

    @Test
    void bannerAndBullScaleWithRunState() {
        List<Card> hand = List.of(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        long bannerDelta = score(hand, r -> r.discardsLeft = 2, "j_banner").chips()
                - score(hand, r -> r.discardsLeft = 2).chips();
        assertThat(bannerDelta).isEqualTo(60); // +30 per remaining discard × 2

        long bullDelta = score(hand, r -> r.money = 10, "j_bull").chips()
                - score(hand, r -> r.money = 10).chips();
        assertThat(bullDelta).isEqualTo(20); // +2 per $1 × 10
    }

    @Test
    void mysticSummitOnlyWithNoDiscards() {
        List<Card> hand = List.of(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        double withDiscards = score(hand, r -> r.discardsLeft = 1, "j_mystic_summit").mult()
                - score(hand, r -> r.discardsLeft = 1).mult();
        double noDiscards = score(hand, r -> r.discardsLeft = 0, "j_mystic_summit").mult()
                - score(hand, r -> r.discardsLeft = 0).mult();
        assertThat(withDiscards).isEqualTo(0.0);
        assertThat(noDiscards).isEqualTo(15.0);
    }

    @Test
    void squareGainsChipsOnFourCardHands() {
        List<Card> four = List.of(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.QUEEN, Suit.SPADES), c(Rank.QUEEN, Suit.HEARTS)); // Two Pair, 4 cards
        long delta = score(four, DEFAULTS, "j_square").chips() - score(four, DEFAULTS).chips();
        assertThat(delta).isEqualTo(4); // gains +4 and applies it this hand
    }

    @Test
    void zanyTriggersOnThreeOfAKindButNotFourOfAKind() {
        List<Card> trips = List.of(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.KING, Suit.CLUBS), c(Rank.TWO, Suit.DIAMONDS), c(Rank.THREE, Suit.SPADES));
        List<Card> quads = List.of(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.DIAMONDS), c(Rank.TWO, Suit.SPADES));
        assertThat(score(trips, DEFAULTS, "j_zany").mult() - score(trips, DEFAULTS).mult()).isEqualTo(12.0);
        // exact-count containment: Four of a Kind does NOT contain Three of a Kind
        assertThat(score(quads, DEFAULTS, "j_zany").mult() - score(quads, DEFAULTS).mult()).isEqualTo(0.0);
    }

    @Test
    void slyDoesNotTriggerOnFourOfAKind() {
        List<Card> pair = List.of(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        List<Card> quads = List.of(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.DIAMONDS), c(Rank.TWO, Suit.SPADES));
        assertThat(score(pair, DEFAULTS, "j_sly_joker").chips() - score(pair, DEFAULTS).chips()).isEqualTo(50);
        assertThat(score(quads, DEFAULTS, "j_sly_joker").chips() - score(quads, DEFAULTS).chips()).isEqualTo(0);
    }

    @Test
    void scholarGivesCompoundChipsAndMultOnAces() {
        List<Card> aces = List.of(c(Rank.ACE, Suit.SPADES), c(Rank.ACE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        ScoreResult with = score(aces, DEFAULTS, "j_scholar");
        ScoreResult without = score(aces, DEFAULTS);
        assertThat(with.chips() - without.chips()).isEqualTo(40); // two Aces × +20
        assertThat(with.mult() - without.mult()).isEqualTo(8.0);  // two Aces × +4 (same rule)
    }

    @Test
    void aceCountsAsOddNotEven() {
        // Pair of Aces: Odd Todd counts them (+31 each), Even Steven does NOT.
        List<Card> aces = List.of(c(Rank.ACE, Suit.SPADES), c(Rank.ACE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        long oddDelta = score(aces, DEFAULTS, "j_odd_todd").chips() - score(aces, DEFAULTS).chips();
        assertThat(oddDelta).isEqualTo(62); // two Aces × +31

        double evenDelta = score(aces, DEFAULTS, "j_even_steven").mult() - score(aces, DEFAULTS).mult();
        assertThat(evenDelta).isEqualTo(0.0); // Aces are not even
    }
}
