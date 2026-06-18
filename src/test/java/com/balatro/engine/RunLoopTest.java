package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.TestSupport.heartsKings;
import static com.balatro.engine.TestSupport.jokers;
import static com.balatro.engine.TestSupport.stoneDeck;
import static com.balatro.engine.card.Rank.EIGHT;
import static com.balatro.engine.card.Rank.FOUR;
import static com.balatro.engine.card.Rank.SIX;
import static com.balatro.engine.card.Rank.TEN;
import static com.balatro.engine.card.Rank.TWO;
import static com.balatro.engine.card.Suit.CLUBS;
import static com.balatro.engine.card.Suit.DIAMONDS;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static com.balatro.engine.game.Blinds.BlindType.BIG;
import static com.balatro.engine.game.Blinds.BlindType.BOSS;
import static com.balatro.engine.game.Blinds.BlindType.SMALL;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.game.Blinds;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.net.ClientView;
import com.balatro.engine.net.ServerUpdate;
import com.balatro.engine.state.Deck;
import com.balatro.engine.state.Ruleset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunLoopTest {

    private final Ruleset std = Ruleset.standard();

    @Test
    void blindRequirementsMatchVanillaScaling() {
        assertThat(Blinds.requirement(1, SMALL, std)).isEqualTo(300);
        assertThat(Blinds.requirement(1, BIG, std)).isEqualTo(450);
        assertThat(Blinds.requirement(1, BOSS, std)).isEqualTo(600);
        assertThat(Blinds.requirement(2, SMALL, std)).isEqualTo(800);
        assertThat(Blinds.requirement(8, BOSS, std)).isEqualTo(100000);
        assertThat(Blinds.getBlindAmount(9, std)).isEqualTo(110000); // formula beyond ante 8
    }

    @Test
    void clearingBlindsAdvancesThroughAntes() {
        // Stone deck: scores well and is immune to suit/face boss debuffs.
        Run win = new Run(std, "WIN", stoneDeck(200), jokers("j_joker", "j_joker", "j_joker"));
        assertThat(win.ante).isEqualTo(1);
        assertThat(win.blind).isEqualTo(SMALL);
        assertThat(win.phase).isEqualTo(Run.Phase.BLIND_SELECT);

        win.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        assertThat(win.phase).isEqualTo(Run.Phase.SHOP);

        win.proceed();
        assertThat(win.blind).isEqualTo(BIG);
        assertThat(win.requirement).isEqualTo(450);

        win.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        win.proceed();
        assertThat(win.blind).isEqualTo(BOSS);
        assertThat(win.boss).isNotNull();                 // a specific boss was chosen
        assertThat(win.requirement).isEqualTo(600);       // ante-1 bosses are all 2x

        win.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        win.proceed();
        assertThat(win.ante).isEqualTo(2);
        assertThat(win.blind).isEqualTo(SMALL);
    }

    @Test
    void runningOutOfHandsUnderRequirementLoses() {
        Ruleset oneHand = new Ruleset("OneHand", 4, 1, 3, 5, 1.0, 8, std.blindBaseAmounts());
        List<Card> weak = List.of(c(TWO, SPADES), c(FOUR, HEARTS), c(SIX, CLUBS), c(EIGHT, DIAMONDS), c(TEN, SPADES));
        Run lose = new Run(oneHand, "LOSE", Deck.of(new java.util.ArrayList<>(weak)), List.of());
        lose.play(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        assertThat(lose.phase).isEqualTo(Run.Phase.RUN_LOST);
    }

    @Test
    void clientViewAndSubmitFormTheAuthoritativeContract() {
        Run run = new Run(std, "VIEW", heartsKings(100), jokers("j_joker"));

        ClientView v = run.view();
        assertThat(v.hand()).hasSize(run.state.handSize);
        assertThat(v.jokers()).anyMatch(j -> "Joker".equals(j.get("name")));
        assertThat(v.ante()).isEqualTo(1);
        assertThat(v.requirement()).isEqualTo(300);

        ServerUpdate up = run.submit(new Intent.PlayHand(List.of(0, 1, 2, 3, 4)));
        assertThat(up.accepted()).isTrue();
        assertThat(up.replay()).isNotEmpty();
        assertThat(up.view().phase()).isEqualTo("SHOP");

        ServerUpdate bad = run.submit(new Intent.PlayHand(List.of(0, 0)));
        assertThat(bad.accepted()).isFalse();
    }
}
