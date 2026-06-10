package com.balatromp.engine;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Rank;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.joker.Joker;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.joker.def.DataJoker;
import com.balatromp.engine.joker.def.JokerDefLibrary;
import com.balatromp.engine.rng.QueueSet;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoreResult;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Generates {@code build/preview-fixtures.json}: deterministic scoring scenarios
 * with the SERVER engine's result as the oracle, serialized exactly as the client
 * view sends them (CardViews + joker def/state + run/deck stats). The Node script
 * {@code preview.test.mjs} runs the client {@code preview.js} against these and
 * asserts it matches — proving the client preview equals the server engine.
 */
class PreviewFixtureGenerator {

    private final ObjectMapper json = new ObjectMapper();
    private final List<Map<String, Object>> fixtures = new ArrayList<>();

    @Test
    void writeFixtures() throws Exception {
        // 1. bare pair
        scenario("pair", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)), List.of());

        // 2. flush + Plain Joker
        scenario("flush+joker", play(c(Rank.TWO, Suit.HEARTS), c(Rank.FOUR, Suit.HEARTS),
                c(Rank.SIX, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS), c(Rank.TEN, Suit.HEARTS)),
                List.of(), "j_joker");

        // 3. Greedy on a diamond pair (per-card, selection-dependent)
        scenario("greedy", play(c(Rank.NINE, Suit.DIAMONDS), c(Rank.NINE, Suit.DIAMONDS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.SPADES)),
                List.of(), "j_greedy_joker");

        // 4. Walkie Talkie compound on Two Pair of 10s/4s
        scenario("walkie", play(c(Rank.TEN, Suit.SPADES), c(Rank.TEN, Suit.HEARTS),
                c(Rank.FOUR, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS), c(Rank.SEVEN, Suit.SPADES)),
                List.of(), "j_walkie_talkie");

        // 5. The Order xMult on a Straight
        scenario("order", play(c(Rank.FIVE, Suit.SPADES), c(Rank.SIX, Suit.HEARTS),
                c(Rank.SEVEN, Suit.CLUBS), c(Rank.EIGHT, Suit.DIAMONDS), c(Rank.NINE, Suit.SPADES)),
                List.of(), "j_order");

        // 6. Abstract (deck-stat: jokers owned) + two inert jokers
        scenario("abstract", play(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), "j_abstract", "j_golden", "j_golden");

        // 7. Baron: held Kings give xMult (held-card, selection-dependent on what you DON'T play)
        scenario("baron", play(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                play(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS)), "j_baron");

        // 8. Scholar compound (Aces) + Foil/Bonus card editions/enhancements
        Card foilAce = new Card(Rank.ACE, Suit.SPADES, Enhancement.BONUS, Edition.FOIL, Seal.NONE);
        scenario("scholar+editions", List.of(foilAce, c(Rank.ACE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), "j_scholar");

        // 9. Rough Gem (DOLLARS during scoring): money must NOT leak into the preview score
        scenario("rough-gem", play(c(Rank.NINE, Suit.DIAMONDS), c(Rank.NINE, Suit.DIAMONDS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.SPADES)),
                List.of(), "j_rough_gem");

        // 10. Cavendish flat x3 mult
        scenario("cavendish", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), "j_cavendish");

        // 11. Triboulet x2 per scored King/Queen
        scenario("triboulet", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.QUEEN, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), "j_triboulet");

        // 12. Joker Stencil: xMult scales with empty joker slots (deck-stat path)
        scenario("stencil", play(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), "j_joker_stencil");

        // 13. Hanging Chad: the first scored card triggers 3x (ScoredFirst + REPETITIONS 2)
        scenario("hanging-chad", play(c(Rank.KING, Suit.SPADES), c(Rank.KING, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), "j_hanging_chad");

        // 14. Seeing Double: a scoring Club + another suit -> x2 (ScoringContainsSuit)
        scenario("seeing-double", play(c(Rank.NINE, Suit.CLUBS), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.SPADES), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), "j_seeing_double");

        // 15. Four Fingers: four Hearts (4 cards played) form a Flush
        scenario("four-fingers", play(c(Rank.TWO, Suit.HEARTS), c(Rank.FIVE, Suit.HEARTS),
                c(Rank.EIGHT, Suit.HEARTS), c(Rank.JACK, Suit.HEARTS)), List.of(), "j_four_fingers");

        // 15b. Four Fingers, 5 cards played: only the 4 Hearts score, the odd Spade does not
        scenario("four-fingers-5", play(c(Rank.TWO, Suit.HEARTS), c(Rank.FIVE, Suit.HEARTS),
                c(Rank.EIGHT, Suit.HEARTS), c(Rank.JACK, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), "j_four_fingers");

        // 16. Shortcut: 3-5-6-7-9 (gaps of one) forms a Straight
        scenario("shortcut", play(c(Rank.THREE, Suit.SPADES), c(Rank.FIVE, Suit.HEARTS),
                c(Rank.SIX, Suit.CLUBS), c(Rank.SEVEN, Suit.DIAMONDS), c(Rank.NINE, Suit.SPADES)),
                List.of(), "j_shortcut");

        // 17. Smeared Joker: Hearts+Diamonds collapse to one suit -> Flush
        scenario("smeared", play(c(Rank.TWO, Suit.HEARTS), c(Rank.FOUR, Suit.DIAMONDS),
                c(Rank.SIX, Suit.HEARTS), c(Rank.EIGHT, Suit.DIAMONDS), c(Rank.TEN, Suit.HEARTS)),
                List.of(), "j_smeared_joker");

        // 18. Mime: retrigger held cards — a held Steel card applies its x1.5 twice
        Card steelHeld = new Card(Rank.QUEEN, Suit.SPADES, Enhancement.STEEL, Edition.NONE, Seal.NONE);
        scenario("mime", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(steelHeld), "j_mime");

        // 19. Dusk: on the final hand (handsLeft=1) all played cards retrigger
        scenario("dusk", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), run -> run.handsLeft = 1, "j_dusk");

        // 20. Photograph: the first scored face card gives x2 (pair of Jacks)
        scenario("photograph", play(c(Rank.JACK, Suit.HEARTS), c(Rank.JACK, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), "j_photograph");

        // 21. Driver's License: x3 when >=16 enhanced cards in the deck
        scenario("drivers-license", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> {
                    for (int i = 0; i < 16; i++) {
                        run.deckComposition.add(new Card(Rank.TWO, Suit.CLUBS, Enhancement.BONUS,
                                Edition.NONE, Seal.NONE));
                    }
                }, "j_drivers_license");

        // 22. Blackboard: x3 when all held cards are Spades/Clubs
        scenario("blackboard", play(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                play(c(Rank.KING, Suit.SPADES), c(Rank.QUEEN, Suit.CLUBS)), "j_blackboard");

        // 23. Pareidolia: Scary Face's +30/face applies to non-face cards too
        scenario("pareidolia", play(c(Rank.TWO, Suit.HEARTS), c(Rank.TWO, Suit.SPADES),
                c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.CLUBS), c(Rank.FIVE, Suit.DIAMONDS)),
                List.of(), "j_scary_face", "j_pareidolia");

        // 24. Splash: every played card scores (the three off-pair cards add chips)
        scenario("splash", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), "j_splash");

        // 25. Green Joker with pre-set state (m=3) — validates the client State value read
        scenario("green-joker", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), run -> run.jokerState(run.jokers().get(0)).put("m", 3), "j_green_joker");

        // 26. Bootstraps: +2 Mult per $5 (money=25 -> +10)
        scenario("bootstraps", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.money = 25, "j_bootstraps");

        // 27. Erosion: +4 Mult per card below 52 (deck of 50 -> +8)
        scenario("erosion", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> {
                    for (int i = 0; i < 50; i++) run.deckComposition.add(c(Rank.TWO, Suit.CLUBS));
                }, "j_erosion");

        Files.createDirectories(Path.of("build"));
        Files.write(Path.of("build/preview-fixtures.json"), json.writeValueAsBytes(fixtures));
    }

    // --- scenario building ----------------------------------------------------

    private static Card c(Rank r, Suit s) {
        return new Card(r, s);
    }

    private static List<Card> play(Card... cards) {
        return List.of(cards);
    }

    private void scenario(String name, List<Card> played, List<Card> held, String... jokerKeys) {
        scenario(name, played, held, null, jokerKeys);
    }

    private void scenario(String name, List<Card> played, List<Card> held,
            java.util.function.Consumer<RunState> tweak, String... jokerKeys) {
        RunState run = new RunState();
        run.rng = new RandomStreams("FIXTURE");
        run.queues = new QueueSet(run.rng);
        List<Joker> jokers = new ArrayList<>();
        for (String k : jokerKeys) {
            Joker j = JokerLibrary.create(k);
            jokers.add(j);
            run.addJoker(j);
        }
        // Tweak runs AFTER jokers are added, so it can set both run fields and joker state.
        if (tweak != null) tweak.accept(run);

        ScoreResult expected = new ScoringEngine().preview(played, held, run, run.rng);

        Map<String, Object> fx = new LinkedHashMap<>();
        fx.put("name", name);
        fx.put("played", played.stream().map(PreviewFixtureGenerator::cardMap).toList());
        fx.put("held", held.stream().map(PreviewFixtureGenerator::cardMap).toList());
        fx.put("jokers", jokers.stream().map(j -> jokerMap(j, run)).toList());
        fx.put("run", runMap(run, jokers.size()));
        fx.put("expected", Map.of("chips", expected.chips(), "mult", expected.mult(), "score", expected.score()));
        fixtures.add(fx);
    }

    private static Map<String, Object> cardMap(Card c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rank", c.rank.name());
        m.put("suit", c.suit.name());
        m.put("enhancement", c.enhancement.name());
        m.put("edition", c.edition.name());
        m.put("seal", c.seal.name());
        m.put("permaChips", c.permaChips);
        m.put("permaMult", c.permaMult);
        return m;
    }

    private Map<String, Object> jokerMap(Joker j, RunState run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", j.key());
        Object def = (j instanceof DataJoker dj) ? dj.def() : JokerDefLibrary.get(j.key());
        m.put("def", def);
        m.put("state", run.jokerState(j));
        return m;
    }

    private Map<String, Object> runMap(RunState run, int jokerCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("money", run.money);
        m.put("handsLeft", run.handsLeft);
        m.put("discardsLeft", run.discardsLeft);
        m.put("ante", run.ante);
        m.put("handSize", run.handSize);
        m.put("jokerCount", jokerCount);
        Map<String, Object> deck = new LinkedHashMap<>();
        deck.put("size", run.deckComposition.size());
        deck.put("remaining", run.deck != null ? run.deck.remaining() : 0);
        // Match Run.view(): count enhancements across the full deck composition.
        Map<String, Integer> enh = new LinkedHashMap<>();
        for (Card cc : run.deckComposition) {
            if (cc.enhancement != Enhancement.NONE) enh.merge(cc.enhancement.name(), 1, Integer::sum);
        }
        deck.put("enhancements", enh);
        m.put("deck", deck);
        Map<String, Object> levels = new LinkedHashMap<>();
        for (HandType t : HandType.values()) levels.put(t.display, run.handLevel(t));
        m.put("handLevels", levels);
        return m;
    }
}
