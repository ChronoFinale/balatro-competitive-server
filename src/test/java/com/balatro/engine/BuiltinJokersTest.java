package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.Deck;
import com.balatro.engine.state.RunState;
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
            "j_wily", "j_clever", "j_devious", "j_crafty", "j_scholar",
            "j_smiley", "j_walkie_talkie", "j_fibonacci", "j_shoot_the_moon",
            "j_baron", "j_runner", "j_wee",
            "j_duo", "j_trio", "j_family", "j_order", "j_tribe",
            "j_arrowhead", "j_onyx_agate", "j_sock_and_buskin", "j_flash", "j_trousers",
            "j_hiker", "j_midas_mask", "j_vampire",
            "j_blue_joker", "j_abstract", "j_stone", "j_steel_joker",
            "j_misprint", "j_bloodstone");

    private ScoreResult score(List<Card> played, Consumer<RunState> cfg, String... jokerKeys) {
        RunState run = new RunState();
        cfg.accept(run);
        for (String k : jokerKeys) run.addJoker(JokerLibrary.create(k));
        return new ScoringEngine().score(played, List.of(), run, new RandomStreams("T"));
    }

    private ScoreResult scoreHeld(List<Card> played, List<Card> held, String... jokerKeys) {
        RunState run = new RunState();
        for (String k : jokerKeys) run.addJoker(JokerLibrary.create(k));
        return new ScoringEngine().score(played, held, run, new RandomStreams("T"));
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
    void walkieTalkieCompoundOnTensAndFours() {
        // Two Pair of 10s and 4s -> all four score; Walkie fires per 10/4: +10 Chips & +4 Mult each.
        List<Card> twoPair = List.of(c(Rank.TEN, Suit.SPADES), c(Rank.TEN, Suit.HEARTS),
                c(Rank.FOUR, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS), c(Rank.SEVEN, Suit.SPADES));
        ScoreResult with = score(twoPair, DEFAULTS, "j_walkie_talkie");
        ScoreResult without = score(twoPair, DEFAULTS);
        assertThat(with.chips() - without.chips()).isEqualTo(40); // 4 cards × +10
        assertThat(with.mult() - without.mult()).isEqualTo(16.0); // 4 cards × +4
    }

    @Test
    void fibonacciCountsAcesAndLowRanks() {
        // Pair of Aces: both are Fibonacci ranks (A) -> +8 Mult each.
        List<Card> aces = List.of(c(Rank.ACE, Suit.SPADES), c(Rank.ACE, Suit.HEARTS),
                c(Rank.TEN, Suit.CLUBS), c(Rank.SEVEN, Suit.CLUBS), c(Rank.SIX, Suit.DIAMONDS));
        assertThat(score(aces, DEFAULTS, "j_fibonacci").mult() - score(aces, DEFAULTS).mult())
                .isEqualTo(16.0);
    }

    @Test
    void baronMultipliesPerHeldKing() {
        List<Card> played = List.of(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        List<Card> twoKingsHeld = List.of(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS));
        double with = scoreHeld(played, twoKingsHeld, "j_baron").mult();
        double without = scoreHeld(played, twoKingsHeld).mult();
        assertThat(with).isEqualTo(without * 1.5 * 1.5); // x1.5 per held King
    }

    @Test
    void runnerGainsChipsOnStraights() {
        List<Card> straight = List.of(c(Rank.FIVE, Suit.SPADES), c(Rank.SIX, Suit.HEARTS),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.EIGHT, Suit.DIAMONDS), c(Rank.NINE, Suit.SPADES));
        assertThat(score(straight, DEFAULTS, "j_runner").chips() - score(straight, DEFAULTS).chips())
                .isEqualTo(15); // gains +15 and applies it this hand
    }

    @Test
    void xMultContainsFamily() {
        // The Duo: x2 Mult if hand contains a Pair.
        List<Card> pair = List.of(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        assertThat(score(pair, DEFAULTS, "j_duo").mult()).isEqualTo(score(pair, DEFAULTS).mult() * 2);

        // The Family: x4 if hand contains a Four of a Kind.
        List<Card> quads = List.of(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.DIAMONDS), c(Rank.TWO, Suit.SPADES));
        assertThat(score(quads, DEFAULTS, "j_family").mult()).isEqualTo(score(quads, DEFAULTS).mult() * 4);
        // ...and The Duo does NOT fire on Four of a Kind (no Pair part)
        assertThat(score(quads, DEFAULTS, "j_duo").mult()).isEqualTo(score(quads, DEFAULTS).mult());
    }

    @Test
    void gemSuitJokers() {
        List<Card> spadeHand = List.of(c(Rank.TWO, Suit.SPADES), c(Rank.FOUR, Suit.SPADES),
                c(Rank.SIX, Suit.SPADES), c(Rank.EIGHT, Suit.SPADES), c(Rank.TEN, Suit.SPADES));
        assertThat(score(spadeHand, DEFAULTS, "j_arrowhead").chips() - score(spadeHand, DEFAULTS).chips())
                .isEqualTo(250); // 5 Spades × +50
        List<Card> clubHand = List.of(c(Rank.TWO, Suit.CLUBS), c(Rank.FOUR, Suit.CLUBS),
                c(Rank.SIX, Suit.CLUBS), c(Rank.EIGHT, Suit.CLUBS), c(Rank.TEN, Suit.CLUBS));
        assertThat(score(clubHand, DEFAULTS, "j_onyx_agate").mult() - score(clubHand, DEFAULTS).mult())
                .isEqualTo(35.0); // 5 Clubs × +7
    }

    @Test
    void sockAndBuskinRetriggersFaces() {
        // Pair of Kings: Sock & Buskin retriggers each face once -> each King's chips count twice.
        List<Card> kings = List.of(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        long delta = score(kings, DEFAULTS, "j_sock_and_buskin").chips() - score(kings, DEFAULTS).chips();
        assertThat(delta).isEqualTo(20); // two Kings re-scored × 10 base chips
    }

    @Test
    void hikerPermanentlyBuffsScoredCards() {
        // Flush -> all five score; Hiker adds +5 permaChips to each.
        List<Card> flush = List.of(c(Rank.TWO, Suit.HEARTS), c(Rank.FOUR, Suit.HEARTS),
                c(Rank.SIX, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.TEN, Suit.HEARTS));
        scoreHeld(flush, List.of(), "j_hiker");
        assertThat(flush).allSatisfy(card -> assertThat(card.permaChips).isEqualTo(5));
    }

    @Test
    void midasMaskGoldsScoredFaceCards() {
        // Pair of Kings (faces that score) -> both become Gold; the non-scoring 2 stays plain.
        Card k1 = c(Rank.KING, Suit.SPADES);
        Card k2 = c(Rank.KING, Suit.HEARTS);
        Card two = c(Rank.TWO, Suit.CLUBS);
        List<Card> pair = List.of(k1, k2, two, c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        scoreHeld(pair, List.of(), "j_midas_mask");
        assertThat(k1.enhancement).isEqualTo(Enhancement.GOLD);
        assertThat(k2.enhancement).isEqualTo(Enhancement.GOLD);
        assertThat(two.enhancement).isEqualTo(Enhancement.NONE); // didn't score -> untouched
    }

    @Test
    void vampireStripsEnhancementsAndGrows() {
        Card bonusK1 = new Card(Rank.KING, Suit.SPADES, Enhancement.BONUS, Edition.NONE, Seal.NONE);
        Card bonusK2 = new Card(Rank.KING, Suit.HEARTS, Enhancement.BONUS, Edition.NONE, Seal.NONE);
        List<Card> pair = List.of(bonusK1, bonusK2, c(Rank.TWO, Suit.CLUBS),
                c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        ScoreResult r = scoreHeld(pair, List.of(), "j_vampire");
        // both enhanced Kings had their enhancement stripped
        assertThat(bonusK1.enhancement).isEqualTo(Enhancement.NONE);
        assertThat(bonusK2.enhancement).isEqualTo(Enhancement.NONE);
        // and Vampire applied x(1 + 0.1*2) = x1.2 this hand
        ScoreResult baseline = scoreHeld(
                List.of(new Card(Rank.KING, Suit.SPADES, Enhancement.BONUS, Edition.NONE, Seal.NONE),
                        new Card(Rank.KING, Suit.HEARTS, Enhancement.BONUS, Edition.NONE, Seal.NONE),
                        c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of());
        assertThat(r.mult()).isEqualTo(baseline.mult() * 1.2);
    }

    @Test
    void probabilisticJokers() {
        List<Card> hand = List.of(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        // Misprint: random +0..+23 Mult, deterministic per seed.
        double misprint = score(hand, DEFAULTS, "j_misprint").mult() - score(hand, DEFAULTS).mult();
        assertThat(misprint).isBetween(0.0, 23.0);
        assertThat(score(hand, DEFAULTS, "j_misprint").mult())
                .isEqualTo(score(hand, DEFAULTS, "j_misprint").mult()); // reproducible

        // Bloodstone: hearts get a 1/2 chance x1.5; the result is deterministic per seed
        // and never reduces mult.
        List<Card> hearts = List.of(c(Rank.TWO, Suit.HEARTS), c(Rank.FOUR, Suit.HEARTS),
                c(Rank.SIX, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.TEN, Suit.HEARTS));
        double withBlood = score(hearts, DEFAULTS, "j_bloodstone").mult();
        double base = score(hearts, DEFAULTS).mult();
        assertThat(withBlood).isGreaterThanOrEqualTo(base);
        assertThat(score(hearts, DEFAULTS, "j_bloodstone").mult()).isEqualTo(withBlood); // reproducible
    }

    @Test
    void deckAndRunStatJokers() {
        List<Card> hand = List.of(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));

        // Abstract Joker: +3 Mult per Joker. With itself + 2 inert jokers = 3 jokers -> +9.
        double abs = score(hand, DEFAULTS, "j_abstract", "j_golden", "j_golden").mult()
                - score(hand, DEFAULTS, "j_golden", "j_golden").mult();
        assertThat(abs).isEqualTo(9.0);

        // Steel Joker: x0.2 Mult per Steel card in the full deck. 3 Steel -> x1.6.
        Consumer<RunState> steel = r -> {
            for (int i = 0; i < 3; i++) {
                r.deckComposition.add(new Card(Rank.KING, Suit.SPADES, Enhancement.STEEL, Edition.NONE, Seal.NONE));
            }
        };
        assertThat(score(hand, steel, "j_steel_joker").mult()).isEqualTo(score(hand, steel).mult() * 1.6);

        // Stone Joker: +25 Chips per Stone card in the deck. 2 Stone -> +50.
        Consumer<RunState> stone = r -> {
            for (int i = 0; i < 2; i++) {
                r.deckComposition.add(new Card(Rank.TWO, Suit.SPADES, Enhancement.STONE, Edition.NONE, Seal.NONE));
            }
        };
        assertThat(score(hand, stone, "j_stone").chips() - score(hand, stone).chips()).isEqualTo(50);

        // Blue Joker: +2 Chips per card remaining in the deck. A 10-card deck -> +20.
        Consumer<RunState> blue = r -> {
            List<Card> ten = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) ten.add(c(Rank.FIVE, Suit.HEARTS));
            r.deck = Deck.of(ten);
        };
        assertThat(score(hand, blue, "j_blue_joker").chips() - score(hand, blue).chips()).isEqualTo(20);
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
