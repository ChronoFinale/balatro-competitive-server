package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.TestSupport.score;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Suit.HEARTS;
import static com.balatro.engine.card.Suit.SPADES;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.engine.joker.def.JokerDef;
import com.balatro.engine.joker.def.Jokers;
import com.balatro.engine.joker.def.Target;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Scoring applies each joker's effect <b>sequentially to the running mult, in joker order</b> — NOT
 * "all + then all ×". So roster order changes the score: a {@code +mult} joker LEFT of a {@code ×mult}
 * joker gets included in the multiply; to its RIGHT it doesn't. This positional ordering is the core of
 * Balatro strategy, and this test guards it from silent regression.
 */
class JokerOrderTest {

    @Test
    void effectsApplyInJokerOrderNotAddThenMultiply() {
        JokerDef plus = Jokers.common("t_plus", "Plus").cost(1).desc("+10 Mult")
                .whenHand().add(Target.MULT, 10).build();
        JokerDef times = Jokers.common("t_times", "Times").cost(1).desc("x3 Mult")
                .whenHand().multiply(Target.MULT, 3).build();

        List<Card> pairOfKings = List.of(c(KING, HEARTS), c(KING, SPADES)); // 30 chips, base 2 Mult

        double plusThenTimes = score(List.of(new DataJoker(plus), new DataJoker(times)), pairOfKings).score();
        double timesThenPlus = score(List.of(new DataJoker(times), new DataJoker(plus)), pairOfKings).score();

        assertThat(plusThenTimes).isEqualTo(30.0 * ((2 + 10) * 3)); // 1080 — '+' lands inside the '×'
        assertThat(timesThenPlus).isEqualTo(30.0 * ((2 * 3) + 10)); // 480  — '+' lands after the '×'
        assertThat(plusThenTimes).isNotEqualTo(timesThenPlus);      // order is positional, not arithmetic
    }
}
