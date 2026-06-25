package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.TestSupport.score;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Suit.DIAMONDS;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.grammar.JokerDef;
import com.balatro.dsl.Jokers;
import com.balatro.grammar.Effect;
import com.balatro.dsl.Val;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A joker's numbers are <b>declared, named properties</b>, and its effect is a function over them
 * ({@code mult(Val.prop("mult"))}) — not anonymous magic literals. This is the language core: declare
 * your data, write functions over it.
 */
class JokerPropertyTest {

    @Test
    void suitJokerDeclaresItsParametersAsNamedProperties() {
        // Greedy/Lusty/Wrathful/Gluttonous are one template; the suit and mult are declared props.
        // Enum props are normalized to their name() so they survive a JSON round-trip unchanged.
        JokerDef greedy = ((DataJoker) JokerLibrary.create("j_greedy_joker")).def();
        assertThat(greedy.props()).containsEntry("mult", 3).containsEntry("suit", DIAMONDS.name());
    }

    @Test
    void theEffectIsAFunctionOfTheProperty() {
        // Same shape, different declared prop -> different score. Proof the effect reads the binding,
        // not a baked-in literal. Pair of Kings = 30 chips, base 2 Mult; the joker adds prop("mult").
        JokerDef three = Jokers.common("t_three", "T3").cost(1).desc("+mult Mult")
                .prop("mult", 3).whenHand().add(Effect.Term.MULT, Val.prop("mult")).build();
        JokerDef ten = Jokers.common("t_ten", "T10").cost(1).desc("+mult Mult")
                .prop("mult", 10).whenHand().add(Effect.Term.MULT, Val.prop("mult")).build();

        List<Card> pairOfKings = List.of(c(KING, HEARTS), c(KING, SPADES));
        assertThat(score(List.of(new DataJoker(three)), pairOfKings).score()).isEqualTo(30.0 * (2 + 3));
        assertThat(score(List.of(new DataJoker(ten)), pairOfKings).score()).isEqualTo(30.0 * (2 + 10));
    }
}
