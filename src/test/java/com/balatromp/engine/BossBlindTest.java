package com.balatromp.engine;

import static com.balatromp.engine.TestSupport.c;
import static com.balatromp.engine.TestSupport.jokers;
import static com.balatromp.engine.TestSupport.stoneDeck;
import static com.balatromp.engine.card.Rank.KING;
import static com.balatromp.engine.card.Suit.HEARTS;
import static com.balatromp.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.card.Card;
import com.balatromp.engine.game.Blinds.BlindType;
import com.balatromp.engine.game.BossBlind;
import com.balatromp.engine.game.BossCatalog;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.rng.RandomStreams;
import com.balatromp.engine.scoring.ScoringEngine;
import com.balatromp.engine.state.RunState;
import com.balatromp.engine.state.Ruleset;
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
        run.forcedBoss = new BossBlind("bl_flint", "The Flint", "halve base",
                1, false, 2.0, 5, -1, -1, 0, null, false, true);
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        assertThat(run.state.bossHalveBase).isFalse(); // Chicot disabled the ability
    }

    @Test
    void runAppliesBossOverridesAtBossBlind() {
        Run run = new Run(Ruleset.standard(), "B", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_joker"));
        // Pin a boss: 3x score, 2 hands, -1 hand size.
        run.forcedBoss = new BossBlind("bl_test", "Test Boss", "x3, 2 hands, -1 size",
                1, false, 3.0, 7, 2, -1, -1, null, false);

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
    void matadorPaysWhenPlayingAgainstABossAbility() {
        Run run = new Run(Ruleset.standard(), "M", stoneDeck(300),
                jokers("j_joker", "j_joker", "j_matador"));
        // A boss with an ability (debuffs Spades).
        run.forcedBoss = new BossBlind("bl_test", "Test Boss", "debuff Spades",
                1, false, 2.0, 7, -1, -1, 0, com.balatromp.engine.card.Suit.SPADES, false);
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
        run.forcedBoss = new BossBlind("bl_test", "Test Boss", "x3, 2 hands, -1 size",
                1, false, 3.0, 7, 2, -1, -1, null, false);
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();
        run.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        run.proceed();

        assertThat(run.blind).isEqualTo(BlindType.BOSS);
        assertThat(run.state.handsLeft).isEqualTo(Ruleset.standard().hands()); // override ignored
        assertThat(run.state.handSize).isEqualTo(8);                           // -1 delta ignored
        assertThat(run.requirement).isEqualTo(900);                            // requirement still applies
    }
}
