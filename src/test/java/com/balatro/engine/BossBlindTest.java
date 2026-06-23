package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.game.Blinds.BlindType;
import static com.balatro.dsl.Cond.card;

import com.balatro.engine.game.BossBlind;
import com.balatro.engine.game.BossCatalog;
import com.balatro.dsl.Bosses;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.rng.RandomStreams;
import com.balatro.engine.scoring.ScoringEngine;
import com.balatro.engine.state.RunState;
import com.balatro.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

class BossBlindTest {

    @Test
    void picksRegularBossBeforeFinisherAntes() {
        BossBlind b = BossCatalog.pick(1, new RandomStreams("S"));
        assertThat(b.finisher()).isFalse();
        assertThat(b.minAnte()).isLessThanOrEqualTo(1);
    }

    @Test
    void picksFinisherOnAnte8() {
        assertThat(BossCatalog.isFinisherAnte(8)).isTrue();
        assertThat(BossCatalog.isFinisherAnte(1)).isFalse();
        assertThat(BossCatalog.pick(8, new RandomStreams("S")).finisher()).isTrue();
    }

    @Test
    void selectionIsDeterministicFromSeed() {
        assertThat(BossCatalog.pick(2, new RandomStreams("X")).key())
                .isEqualTo(BossCatalog.pick(2, new RandomStreams("X")).key());
    }

    @Test
    void debuffedCardsDoNotScore() {
        Card k1 = c(KING, HEARTS), k2 = c(KING, SPADES);
        double normal = new ScoringEngine()
                .score(List.of(k1, k2), List.of(), new RunState(), new RandomStreams("D")).score();
        k1.debuffed = true;
        k2.debuffed = true;
        double debuffed = new ScoringEngine()
                .score(List.of(k1, k2), List.of(), new RunState(), new RandomStreams("D")).score();
        assertThat(debuffed).isLessThan(normal); // 30x2=60 -> base only 10x2=20
    }

    @Test
    void flintHalvesBaseChipsAndMult() {
        var played = List.of(c(KING, HEARTS), c(KING, SPADES)); // pair: base 10 chips x 2 mult, +20 from Kings
        RunState normal = new RunState();
        double full = new ScoringEngine().score(played, List.of(), normal, new RandomStreams("F")).score();
        RunState flint = new RunState();
        flint.bossHalveBase = true;
        var r = new ScoringEngine().score(played, List.of(), flint, new RandomStreams("F"));
        // Base 10x2 -> 5x1; the King chips (+20) are added after halving, so it is not simply full/4.
        assertThat(r.chips()).isEqualTo(25L);   // 5 (halved base) + 20 (two Kings)
        assertThat(r.mult()).isEqualTo(1.0);     // 2 -> 1
        assertThat(r.score()).isEqualTo(25.0);
        assertThat(r.score()).isLessThan(full);
    }

    @Test
    void flintIsInThePoolAndDisabledByChicot() {
        Run run = new Run(Ruleset.standard(), "FL", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_chicot"));
        run.forcedBoss = Bosses.of("bl_flint", "The Flint").desc("halve base").halvesBase().build();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        assertThat(run.state.bossHalveBase).isFalse(); // Chicot disabled the ability
    }

    @Test
    void anaglyphDeckGrantsADoubleTagAfterBeatingABoss() {
        Run run = new Run(Ruleset.standard(), "AN", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_joker"), com.balatro.engine.state.Stake.WHITE,
                com.balatro.engine.game.DeckCatalog.get("d_anaglyph"));
        run.forcedBoss = Bosses.of("bl_test", "Test Boss").desc("none").build();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Small
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Big
        run.proceed();
        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        assertThat(run.state.tags).doesNotContain("tag_double"); // boss not yet beaten
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // beat the boss
        assertThat(run.state.tags).contains("tag_double");      // Anaglyph: Double Tag after the boss
    }

    @Test
    void runAppliesBossOverridesAtBossBlind() {
        Run run = new Run(Ruleset.standard(), "B", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_joker"));
        // Pin a boss: 3x score, 2 hands, -1 hand size.
        run.forcedBoss = Bosses.of("bl_test", "Test Boss").desc("x3, 2 hands, -1 size").requirement(3).reward(7).hands(2).handSize(-1).build();

        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Small
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Big
        run.proceed();

        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        assertThat(run.boss.name()).isEqualTo("Test Boss");
        assertThat(run.state.handsLeft).isEqualTo(2);        // handsOverride
        assertThat(run.state.handSize).isEqualTo(7);         // 8 - 1
        assertThat(run.requirement).isEqualTo(900);          // getBlindAmount(1)=300 * 3
    }

    @Test
    void bossEffectTextIsLocalizedPerRunLocale() {
        Run run = new Run(Ruleset.standard(), "LOC", stoneDeck(300),
                jokers("j_greedy_joker", "j_joker", "j_joker"));
        run.forcedBoss = Bosses.of("bl_wall", "The Wall").requirement(4).build(); // effect from localization
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        assertThat(run.blind).isEqualTo(BlindType.BOSS);

        run.viewLocale = "en";
        assertThat(run.view().bossEffect()).isEqualTo("Very large blind (4x score)");
        assertThat(run.view().jokers().get(0).get("description")).isEqualTo("Each played Diamond gives +3 Mult");
        run.viewLocale = "fr"; // server renders ALL ClientView text in French
        assertThat(run.view().bossEffect()).isEqualTo("Très grande blinde (4× score)"); // ${reqMult}=4 from data
        assertThat(run.view().jokers().get(0).get("description")).isEqualTo("Chaque Carreau joué donne +3 Mult");
    }

    @Test
    void viewExposesBossKeyForTheClientAtTheBossBlind() {
        // The real-Balatro bridge faces G.P_BLINDS[view.bossKey] so the player meets the SERVER's boss;
        // bossKey must be the native bl_ key, and null while no boss is active (Small/Big).
        Run run = new Run(Ruleset.standard(), "BK", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_joker"));
        run.forcedBoss = Bosses.of("bl_wall", "The Wall").desc("x3, 2 hands, -1 size").requirement(3).reward(7).hands(2).handSize(-1).build();
        assertThat(run.view().bossKey()).isNull(); // Small blind: no boss yet

        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Small
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Big
        run.proceed();

        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        assertThat(run.view().bossKey()).isEqualTo("bl_wall");
        assertThat(run.view().boss()).isEqualTo("The Wall");
    }

    @Test
    void matadorPaysWhenPlayingAgainstABossAbility() {
        Run run = new Run(Ruleset.standard(), "M", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_matador"));
        // A boss with an ability (debuffs Spades).
        run.forcedBoss = Bosses.of("bl_test", "Test Boss").desc("debuff Spades").reward(7).debuffs(card().suit(com.balatro.engine.card.Suit.SPADES)).build();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        assertThat(run.blind).isEqualTo(BlindType.BOSS);

        int money = run.state.money;
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // play against the ability
        assertThat(run.state.money).isGreaterThanOrEqualTo(money + 8); // Matador's $8 (+ any win economy)
    }

    @Test
    void chicotDisablesTheBossAbilityButNotItsRequirement() {
        Run run = new Run(Ruleset.standard(), "B", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_chicot"));
        run.forcedBoss = Bosses.of("bl_test", "Test Boss").desc("x3, 2 hands, -1 size").requirement(3).reward(7).hands(2).handSize(-1).build();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();

        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        assertThat(run.state.handsLeft).isEqualTo(Ruleset.standard().hands()); // override ignored
        assertThat(run.state.handSize).isEqualTo(8);                           // -1 delta ignored
        assertThat(run.requirement).isEqualTo(900);                            // requirement still applies
    }

    @Test
    void theToothLosesADollarPerCardPlayed() { // the onHandPlayed boss-effect framework
        Run run = new Run(Ruleset.standard(), "TOOTH", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_joker"));
        // Pin The Tooth, unwinnable in one hand (reqMult 100) so the boss hand doesn't end the blind.
        run.forcedBoss = Bosses.of("bl_tooth", "The Tooth").desc("Lose $1 per card played").requirement(100).dollarsPerCard(-1).build();

        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Small
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Big
        run.proceed();

        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        int before = run.state.money;
        run.play(new Intent.PlayHand(List.of(0, 1, 2))); // play 3 cards at The Tooth
        assertThat(run.state.money).isEqualTo(before - 3); // -$1 per card
    }

    @Test
    void theOxZeroesMoneyOnlyOnYourMostPlayedHand() { // PlayedHandIsMostPlayed + AdjustMoney(SET) as data rules
        Run run = bossRunOn(Bosses.of("bl_ox", "The Ox").desc("most-played sets $0")
                .requirement(100).zeroMoneyOnMostPlayed().build());
        // bossRunOn cleared Small + Big with two 5-King hands, so that hand type is the most-played this run.
        run.state.money = 9;
        // Negative control: a Pair (2 Kings) is a different, rarer hand type — not most-played, money stands.
        run.play(new Intent.PlayHand(List.of(0, 1)));
        assertThat(run.state.money).isEqualTo(9);
        // Positive: replay the most-played 5-King hand — The Ox empties the wallet.
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        assertThat(run.state.money).isEqualTo(0);
    }

    @Test
    void theArmDelevelsThePlayedHand() { // DelevelPlayedHand fired at ON_HAND_PLAYED
        Run run = bossRunOn(Bosses.of("bl_arm", "The Arm").desc("delevel played hand")
                .requirement(100).delevelsPlayedHand().build());
        var handType = com.balatro.engine.hand.HandEvaluator.evaluate(
                List.of(run.state.hand.get(0), run.state.hand.get(1), run.state.hand.get(2),
                        run.state.hand.get(3), run.state.hand.get(4))).type();
        run.state.setHandLevel(handType, 3); // raise it so the one-level drop is observable (not floored at 1)
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        assertThat(run.state.handLevel(handType)).isEqualTo(2); // The Arm dropped it one level
    }

    @Test
    void theMouthAllowsOnlyOneHandTypePerRound() { // hand-legality via the shared Cond language
        Run run = bossRunOn(Bosses.of("bl_mouth", "The Mouth").desc("one hand type")
                .requirement(100).requires(com.balatro.dsl.Cond.playedHand().matchesRoundType()).build());

        assertThat(run.play(new Intent.PlayHand(List.of(0, 1))).ok()).isTrue();  // Pair establishes the type
        assertThat(run.play(new Intent.PlayHand(List.of(0))).ok()).isFalse();    // High Card — different type, illegal
        assertThat(run.play(new Intent.PlayHand(List.of(0, 1))).ok()).isTrue();  // another Pair — same type, fine
    }

    @Test
    void theEyeForbidsRepeatHandTypesPerRound() { // hand-legality via the shared Cond language
        Run run = bossRunOn(Bosses.of("bl_eye", "The Eye").desc("no repeats")
                .requirement(100).requires(com.balatro.dsl.Cond.playedHand().firstTimeThisRound()).build());

        assertThat(run.play(new Intent.PlayHand(List.of(0, 1))).ok()).isTrue();  // Pair — first time, fine
        assertThat(run.play(new Intent.PlayHand(List.of(0, 1))).ok()).isFalse(); // Pair again — repeat, illegal
        assertThat(run.play(new Intent.PlayHand(List.of(0))).ok()).isTrue();     // High Card — new type, fine
    }

    @Test
    void theMarkDrawsFaceCardsFaceDown() { // draw-time hidden-information framework
        Run run = bossRunOn(Bosses.of("bl_mark", "The Mark").desc("faces face down")
                .requirement(100).drawsFaceDown(card().isFace()).build());

        // King♥ deck: every drawn card is a face card, so the whole hand is face down — and the
        // ClientView reveals none of their identities (only the uid handle + a faceDown flag).
        assertThat(run.state.hand).isNotEmpty();
        assertThat(run.state.hand).allMatch(c -> c.faceDown);
        var view = run.view();
        assertThat(view.hand()).allMatch(cv -> cv.faceDown() && cv.rank() == null && cv.suit() == null);
        assertThat(view.hand()).allMatch(cv -> cv.uid() != null); // still targetable
    }

    @Test
    void aFaceDownCardIsRevealedOnlyWhenPlayed() {
        // The whole point of face-down: the client is told NOTHING about the card in hand (the view
        // hides it), and learns its identity only when it's played — through the authoritative scoring
        // replay, which the server computes and names. No peeking, even by a tampered client.
        Run run = bossRunOn(Bosses.of("bl_mark", "The Mark").desc("faces face down")
                .requirement(100).drawsFaceDown(card().isFace()).build());

        assertThat(run.view().hand()).allMatch(cv -> cv.rank() == null); // in hand: identity withheld
        var result = run.play(new Intent.PlayHand(List.of(0, 1)));
        // Once played, the King surfaces by name in the replay stream ("Kh") — the "flip over" moment.
        assertThat(result.score().replayLog()).anyMatch(e -> e.source().startsWith("K"));
    }

    @Test
    void theMarkLeavesNonFaceCardsFaceUp() {
        Run run = new Run(Ruleset.standard(), "MARK2", TestSupport.stoneDeck(300),
                jokers("j_joker", "j_joker", "j_joker"));
        run.forcedBoss = Bosses.of("bl_mark", "The Mark").desc("faces face down")
                .requirement(100).drawsFaceDown(card().isFace()).build();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        // Stone TWOs aren't face cards: nothing is hidden.
        assertThat(run.state.hand).noneMatch(c -> c.faceDown);
    }

    @Test
    void theHouseDrawsOnlyTheOpeningHandFaceDown() {
        Run run = bossRunOn(Bosses.of("bl_house", "The House").desc("first hand face down")
                .requirement(100).drawsInitialHandFaceDown().build());

        assertThat(run.state.hand).allMatch(c -> c.faceDown); // opening hand is hidden
        run.play(new Intent.PlayHand(List.of(0, 1))); // play 2 — refilled cards are a later deal
        long stillHidden = run.state.hand.stream().filter(c -> c.faceDown).count();
        long freshFaceUp = run.state.hand.stream().filter(c -> !c.faceDown).count();
        assertThat(freshFaceUp).isEqualTo(2); // the 2 redrawn cards came up face up
        assertThat(stillHidden).isEqualTo(run.state.hand.size() - 2); // the rest of the opening hand stays hidden
    }

    @Test
    void thePillarDebuffsCardsAlreadyPlayedThisAnte() {
        // The boss's debuff IS the shared "played this ante" card condition — no bespoke field.
        BossBlind pillar = Bosses.of("bl_pillar", "The Pillar").desc("x")
                .debuffs(com.balatro.dsl.Cond.card().playedThisAnte()).build();
        Card fresh = c(KING, HEARTS);
        Card replayed = c(KING, HEARTS);
        replayed.playedThisAnte = true;

        var ctxFresh = new com.balatro.engine.joker.EvaluationContext();
        ctxFresh.scoredCard = fresh;
        var ctxReplayed = new com.balatro.engine.joker.EvaluationContext();
        ctxReplayed.scoredCard = replayed;
        assertThat(pillar.debuff().test(ctxFresh)).isFalse();
        assertThat(pillar.debuff().test(ctxReplayed)).isTrue();
    }

    @Test
    void playingAHandFlagsItsCardsAsPlayedThisAnte() { // the marking that feeds The Pillar
        Run run = bossRunOn(Bosses.of("bl_pillar", "The Pillar").desc("played-this-ante debuffed")
                .requirement(100).debuffs(com.balatro.dsl.Cond.card().playedThisAnte()).build());

        Card played = run.state.hand.get(0);
        assertThat(played.playedThisAnte).isFalse();
        run.play(new Intent.PlayHand(List.of(0, 1)));
        assertThat(played.playedThisAnte).isTrue(); // the card object carries the flag back into the deck
    }

    @Test
    void theSerpentDrawsExactlyThreeOnRefill() { // draw-count override framework
        Run run = bossRunOn(Bosses.of("bl_serpent", "The Serpent").desc("always draw 3")
                .requirement(100).drawsExactly(3).build());

        int handSize = run.state.hand.size(); // opening hand is the normal full deal (8)
        assertThat(handSize).isEqualTo(8);
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // play 5 -> 3 left, then draw exactly 3
        assertThat(run.state.hand).hasSize(6); // 3 kept + 3 drawn, NOT refilled back to 8
    }

    @Test
    void theHookDiscardsTwoCardsAfterEachHand() { // post-play forced-discard framework
        Run run = bossRunOn(Bosses.of("bl_hook", "The Hook").desc("discard 2 per hand")
                .requirement(100).discardsRandomAfterPlay(2).build());

        assertThat(run.state.hand).hasSize(8);
        // Play 2 cards: refill restores the hand to 8, then The Hook discards 2 random and refills 2.
        // Net hand size stays 8, but two extra cards were churned out of the deck.
        int deckBefore = run.state.deck.remaining();
        run.play(new Intent.PlayHand(List.of(0, 1)));
        assertThat(run.state.hand).hasSize(8); // still full (discard is followed by a refill)
        // 4 cards left the draw pile this turn: 2 to replace the played pair + 2 to replace the hooked pair.
        assertThat(run.state.deck.remaining()).isEqualTo(deckBefore - 4);
    }

    @Test
    void verdantLeafDebuffsEveryCardUntilAJokerIsSold() { // debuff-all + sell-to-disable framework
        Run run = bossRunOn(Bosses.of("bl_final_leaf", "Verdant Leaf").desc("all debuffed until a sell")
                .requirement(100).debuffs(com.balatro.dsl.Cond.always()).disabledBySellingJoker().build());

        assertThat(run.state.hand).allMatch(cardObj -> cardObj.debuffed); // every card debuffed at the boss
        assertThat(run.sellJoker(0)).isNull();                            // sell any Joker...
        assertThat(run.state.hand).noneMatch(cardObj -> cardObj.debuffed); // ...and the debuff lifts
    }

    @Test
    void crimsonHeartDisablesExactlyOneRandomJokerEachHand() { // per-hand joker-disable framework
        Run run = bossRunOn(Bosses.of("bl_final_heart", "Crimson Heart").desc("disable a joker each hand")
                .requirement(100).disablesRandomJokerEachHand().build());

        // No joker disabled until a hand is actually played.
        assertThat(run.state.jokers()).noneMatch(j -> run.state.jokerFlag(j, "bossDisabled"));
        run.play(new Intent.PlayHand(List.of(0, 1)));
        long disabled = run.state.jokers().stream().filter(j -> run.state.jokerFlag(j, "bossDisabled")).count();
        assertThat(disabled).isEqualTo(1); // exactly one Joker switched off this hand
    }

    @Test
    void amberAcornHidesJokersInTheView() { // joker info-hiding framework
        Run run = bossRunOn(Bosses.of("bl_final_acorn", "Amber Acorn").desc("flip + shuffle jokers")
                .requirement(100).flipsAndShufflesJokers().build());

        // The client sees only card backs: no key/name leaks for any Joker at this blind.
        var jokers = run.view().jokers();
        assertThat(jokers).isNotEmpty();
        assertThat(jokers).allMatch(jv -> Boolean.TRUE.equals(jv.get("faceDown")));
        assertThat(jokers).allMatch(jv -> jv.get("key") == null && jv.get("name") == null);
    }

    @Test
    void ceruleanBellForcesOneCardIntoEveryHand() { // forced-selection framework
        Run run = bossRunOn(Bosses.of("bl_final_bell", "Cerulean Bell").desc("one card forced")
                .requirement(100).forcesOneCardSelected().build());

        // Exactly one held card is force-selected, and the client is told which.
        long forced = run.state.hand.stream().filter(cardObj -> cardObj.forcedSelected).count();
        assertThat(forced).isEqualTo(1);
        int fi = -1;
        for (int i = 0; i < run.state.hand.size(); i++) if (run.state.hand.get(i).forcedSelected) fi = i;
        assertThat(run.view().hand().get(fi).forcedSelected()).isTrue();

        // A play that omits the forced card is rejected; one that includes it is accepted.
        int other = (fi == 0) ? 1 : 0;
        assertThat(run.play(new Intent.PlayHand(List.of(other))).ok()).isFalse();
        assertThat(run.play(new Intent.PlayHand(List.of(fi, other))).ok()).isTrue();
    }

    /** Advance a fresh run to its (forced) Boss blind, ready to play hands of a known type. */
    private static Run bossRunOn(BossBlind forced) {
        Run run = new Run(Ruleset.standard(), "LEGAL", TestSupport.kingDeck(300),
                jokers("j_joker", "j_joker", "j_joker"));
        run.forcedBoss = forced;
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Small
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4))); // clear Big
        run.proceed();
        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        return run;
    }
}
