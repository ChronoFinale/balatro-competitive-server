package com.balatro.engine;

import com.balatro.engine.joker.def.Hand;
import com.balatro.engine.card.Card;
import com.balatro.engine.card.Edition;
import com.balatro.engine.card.Enhancement;
import com.balatro.engine.card.Rank;
import com.balatro.engine.card.Seal;
import com.balatro.engine.card.Suit;
import com.balatro.engine.hand.HandType;
import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.engine.joker.def.JokerDefLibrary;
import com.balatro.engine.rng.QueueSet;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoreResult;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
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

        // 12b. Joker editions: Foil (+50 chips) / Holo (+10 mult) before the joker scores,
        // Polychrome (x1.5 mult) after. Edition lives in the joker's shipped state bag.
        scenario("joker-foil", play(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), r -> r.setJokerEdition(r.jokers().get(0), Edition.FOIL), "j_joker");
        scenario("joker-holo", play(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), r -> r.setJokerEdition(r.jokers().get(0), Edition.HOLOGRAPHIC), "j_joker");
        scenario("joker-poly", play(c(Rank.NINE, Suit.SPADES), c(Rank.NINE, Suit.HEARTS),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), r -> r.setJokerEdition(r.jokers().get(0), Edition.POLYCHROME), "j_joker");

        // 12c. The Flint: base chips + mult are halved before any joker scores.
        scenario("flint", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), r -> r.bossHalveBase = true, "j_joker");

        // 13. Hanging Chad: the first scored card triggers 3x (ScoredFirst + RETRIGGERS 2)
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

        // 28. Raised Fist: +2x lowest held card rank (held 4 and 7 -> lowest 4 -> +8 Mult)
        scenario("raised-fist", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                play(c(Rank.FOUR, Suit.CLUBS), c(Rank.SEVEN, Suit.DIAMONDS)), "j_raised_fist");

        // 29. Yorick with pre-set state (d=46 -> floor(46/23)=2 -> x3) validates StateStep on the client
        scenario("yorick", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), run -> run.jokerState(run.jokers().get(0)).put("d", 46), "j_yorick");

        // 30. Ice Cream after 6 hands: 100 - 5*6 = +70 Chips (validates shipped counter + clamp)
        scenario("ice-cream", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.handsPlayedTotal = 6, "j_ice_cream");

        // 31. Ramen after 50 discards: x(2 - 0.5) = x1.5
        scenario("ramen", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.cardsDiscardedTotal = 50, "j_ramen");

        // 32. Lucky Cat after 4 Lucky triggers: x(1 + 0.25*4) = x2 (shipped LUCKY_TRIGGERS counter)
        scenario("lucky-cat", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.luckyTriggersTotal = 4, "j_lucky_cat");

        // 47. Glass card in multiplayer scores x1.5 (vanilla x2)
        Card glassMp = new Card(Rank.KING, Suit.HEARTS, Enhancement.GLASS, Edition.NONE, Seal.NONE);
        scenario("glass-mp", play(glassMp, c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS)),
                List.of(), run -> run.capabilities = com.balatro.engine.state.Capabilities.MULTIPLAYER);

        // 44. Pacifist: x10 Mult outside a PvP blind (default inPvpBlind=false)
        scenario("pacifist", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), "j_pacifist");

        // 45. Defensive Joker: +125 Chips per life behind (2 behind -> +250)
        scenario("defensive", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> { run.oppLives = 3; run.myLives = 1; }, "j_defensive_joker");

        // 46. Conjoined: in a PvP blind, x(1 + 0.5*oppHandsLeft) capped at x3 (4 left -> x3)
        scenario("conjoined", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> { run.inPvpBlind = true; run.oppHandsLeft = 4; }, "j_conjoined");

        // 43. Throwback after 2 blinds skipped -> x(1 + 0.25*2) = x1.5
        scenario("throwback", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.blindsSkipped = 2, "j_throwback");

        // 42. Seltzer within its 10-hand window (acquired at hand 0, now hand 3) -> retrigger all
        scenario("seltzer", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.handsPlayedTotal = 3, "j_seltzer");

        // 41. Obelisk after a 3-hand non-most-played streak -> x(1 + 0.2*3) = x1.6
        scenario("obelisk", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.obeliskStreak = 3, "j_obelisk");

        // 40. Loyalty Card on the 6th hand (handsPlayedTotal=5) -> x4
        scenario("loyalty-card", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.handsPlayedTotal = 5, "j_loyalty_card");

        // 39. Castle with pre-set chips (3 castle-suit cards discarded -> +9 Chips)
        scenario("castle", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.jokerState(run.jokers().get(0)).put("chips", 9), "j_castle");

        // 37. The Idol (King of Hearts this round): the played KH gives x2
        scenario("idol", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> { run.roundTargets.put("idolRankId", 13); run.roundTargets.put("idolSuit", Suit.HEARTS); }, "j_idol");

        // 38. Ancient (Hearts this round): the played KH gives x1.5
        scenario("ancient", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.roundTargets.put("ancientSuit", Suit.HEARTS), "j_ancient_joker");

        // 35. Baseball Card + two (inert) Uncommon jokers -> x1.5 each = x2.25
        scenario("baseball", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), "j_baseball_card", "j_acrobat", "j_acrobat");

        // 36. Swashbuckler + two other jokers -> adds their sell value to Mult
        scenario("swashbuckler", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), "j_swashbuckler", "j_joker", "j_acrobat");

        // 33. Supernova: pair played twice before -> +2 Mult (HandTypePlays read of shipped map)
        scenario("supernova", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.handTypePlays.put(HandType.PAIR, 2), "j_supernova");

        // 34. Card Sharp: pair already played this round -> x3
        scenario("card-sharp", play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES)),
                List.of(), run -> run.handTypesThisRound.add(HandType.PAIR), "j_card_sharp");

        // Exhaustive baseline sweep: every joker solo on a fixed Pair, so the preview-mirror
        // harness covers the WHOLE catalog, not just the hand-picked stateful cases above. A joker
        // whose preview is unsupported (probabilistic/native) returns null and is skipped by the
        // harness; one that returns a wrong number is caught. This is what makes a vocabulary
        // refactor that desyncs preview.js fail the build for ANY joker, not only the 52 below.
        List<Card> basePair = play(c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
                c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.DIAMONDS));
        for (String key : JokerDefLibrary.all().keySet()) {
            scenario("all:" + key, basePair, List.of(), key);
        }
        // Second sweep on a richer state — a Heart Flush of face cards, final hand, no discards left,
        // money on hand, deeper ante — so flush/final-hand/money/face/ante jokers actually fire instead
        // of scoring an inert base. A divergence on any joker's ACTIVE path now fails the build too.
        List<Card> faceFlush = play(c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.HEARTS),
                c(Rank.JACK, Suit.HEARTS), c(Rank.TEN, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS));
        for (String key : JokerDefLibrary.all().keySet()) {
            scenario("flush:" + key, faceFlush, List.of(c(Rank.EIGHT, Suit.HEARTS)), run -> {
                run.money = 20; run.handsLeft = 1; run.discardsLeft = 0; run.ante = 4;
            }, key);
        }

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
        m.put("rarity", j.info().rarity()); // Baseball Card reads other jokers' rarity
        m.put("cost", j.info().cost());      // Swashbuckler sums other jokers' sell value
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
        // Run-long counters (decay jokers) — mirror Run.view().
        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("HANDS_PLAYED_TOTAL", run.handsPlayedTotal);
        counters.put("ROUNDS_PLAYED", run.roundsPlayedTotal);
        counters.put("CARDS_DISCARDED_TOTAL", run.cardsDiscardedTotal);
        counters.put("LUCKY_TRIGGERS", run.luckyTriggersTotal);
        counters.put("DISCARDS_USED", run.discardsUsedThisRound);
        counters.put("HANDS_PLAYED", run.handsPlayedThisRound);
        Map<String, Object> typePlays = new LinkedHashMap<>();
        run.handTypePlays.forEach((t, n) -> typePlays.put(t.name(), n));
        counters.put("handTypePlays", typePlays);
        counters.put("handTypesThisRound", run.handTypesThisRound.stream().map(Enum::name).toList());
        counters.put("idolRankId", run.roundTargets.get("idolRankId"));
        counters.put("idolSuit", ((Suit) run.roundTargets.get("idolSuit")).name());
        counters.put("ancientSuit", ((Suit) run.roundTargets.get("ancientSuit")).name());
        counters.put("castleSuit", ((Suit) run.roundTargets.get("castleSuit")).name());
        counters.put("todoHand", ((com.balatro.engine.hand.HandType) run.roundTargets.get("todoHand")).name());
        counters.put("OBELISK_STREAK", run.obeliskStreak);
        counters.put("BLINDS_SKIPPED", run.blindsSkipped);
        counters.put("inPvpBlind", run.inPvpBlind);
        counters.put("bossHalveBase", run.bossHalveBase);
        counters.put("multiplayer", run.capabilities.restrictedPools());
        counters.put("OPP_LIVES_BEHIND", Math.max(0, run.oppLives - run.myLives));
        counters.put("OPP_HANDS_LEFT", run.oppHandsLeft);
        counters.put("OPP_CARDS_SOLD", run.oppCardsSold);
        m.put("counters", counters);
        return m;
    }
}
