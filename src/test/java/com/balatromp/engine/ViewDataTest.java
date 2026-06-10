package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.game.Run;
import com.balatromp.engine.joker.JokerLibrary;
import com.balatromp.engine.net.ClientView;
import com.balatromp.engine.state.Deck;
import com.balatromp.engine.state.Ruleset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The view carries everything a client needs for an instant local score preview:
 * each joker's data definition + live state, and deck aggregates — so the client
 * interprets the same JokerDef the server uses (server still authoritative on Play).
 */
class ViewDataTest {

    @Test
    void viewExposesJokerDefStateAndDeckStats() {
        Run run = new Run(Ruleset.standard(), "V", Deck.standard(),
                List.of(JokerLibrary.create("j_abstract")));
        ClientView v = run.view();

        Map<String, Object> joker = v.jokers().get(0);
        assertThat(joker).containsKey("def");    // the JokerDef the client interprets
        assertThat(joker).containsKey("state");  // per-joker live state (for scaling jokers)
        assertThat(joker.get("display")).isEqualTo("+3 Mult"); // Abstract with 1 joker

        Map<String, Object> deck = v.deckStats();
        assertThat(deck.get("size")).isEqualTo(52);
        assertThat(deck).containsKey("remaining");
        assertThat(deck).containsKey("enhancements");
    }
}
