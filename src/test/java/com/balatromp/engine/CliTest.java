package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.net.ClientCli;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CliTest {

    @Test
    void parsesPlay() {
        Map<String, Object> m = ClientCli.parseCommand("play 0 1 2 3 4");
        assertThat(m.get("type")).isEqualTo("playHand");
        @SuppressWarnings("unchecked")
        List<Integer> cards = (List<Integer>) m.get("cards");
        assertThat(cards).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void parsesDiscard() {
        Map<String, Object> m = ClientCli.parseCommand("discard 2 5");
        assertThat(m.get("type")).isEqualTo("discard");
        @SuppressWarnings("unchecked")
        List<Integer> cards = (List<Integer>) m.get("cards");
        assertThat(cards).containsExactly(2, 5);
    }

    @Test
    void parsesBuyAndReroll() {
        assertThat(ClientCli.parseCommand("buy 1").get("index")).isEqualTo(1);
        assertThat(ClientCli.parseCommand("buy 1").get("type")).isEqualTo("buyJoker");
        assertThat(ClientCli.parseCommand("reroll").get("type")).isEqualTo("reroll");
    }

    @Test
    void parsesNewWithOptionalSeed() {
        assertThat(ClientCli.parseCommand("new").get("type")).isEqualTo("newRun");
        assertThat(ClientCli.parseCommand("new ABC").get("seed")).isEqualTo("ABC");
    }

    @Test
    void parsesProceedAndGo() {
        assertThat(ClientCli.parseCommand("proceed").get("type")).isEqualTo("proceed");
        assertThat(ClientCli.parseCommand("go").get("type")).isEqualTo("proceed");
    }

    @Test
    void unknownCommandReturnsNull() {
        assertThat(ClientCli.parseCommand("foobar")).isNull();
    }
}
