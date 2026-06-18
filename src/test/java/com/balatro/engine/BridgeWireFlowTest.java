package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.net.GameServer;
import com.balatro.engine.state.Ruleset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end wire test of the exact path the real-Balatro bridge drives: HTTP login → TCP auth → newRun
 * (with the deck/stake the New Run screen forwards) → skip to the Boss blind. Asserts the SERIALIZED JSON
 * (not just Run state) carries the fields the Lua mod scrapes — deck/stake names, the active-blind view
 * scalars, and {@code bossKey} in native {@code bl_} form. The mod faces {@code G.P_BLINDS[bossKey]}, so
 * this is the contract that makes server-driven boss blinds render.
 */
class BridgeWireFlowTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void deckStakeViewShapeAndBossKeyOverTheWire() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            String token = login(server.port(), "bridge");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", server.tcpPort()), 2000);
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                send(out, Map.of("type", "auth", "seq", 0, "token", token));
                assertThat(JSON.readTree(in.readLine()).path("type").asText()).isEqualTo("authed");

                // The New Run screen forwards native deck b_blue -> d_blue and stake 3.
                send(out, Map.of("type", "newRun", "seq", 1, "seed", "BRIDGE001",
                        "deck", "d_blue", "stake", "3"));
                JsonNode v1 = JSON.readTree(in.readLine()).path("view");
                assertThat(v1.path("ante").asInt()).isEqualTo(1);
                assertThat(v1.path("deck").asText()).isEqualTo("Blue Deck");
                assertThat(v1.path("stake").asText()).isEqualTo("Green Stake");
                assertThat(v1.path("hand").size()).isGreaterThan(0);

                // The active-blind scalars the mod reconciles (reconcile()/parse_view) must all serialize.
                assertThat(v1.path("requirement").asLong()).isGreaterThan(0);
                assertThat(v1.has("handsLeft")).isTrue();
                assertThat(v1.has("discardsLeft")).isTrue();
                assertThat(v1.has("money")).isTrue();
                assertThat(v1.has("handSize")).isTrue();
                assertThat(v1.path("deckStats").has("remaining")).isTrue();
                // No boss yet at the Small blind: bossKey serializes to JSON null.
                assertThat(v1.path("bossKey").isNull()).isTrue();

                // Skip Small -> Big -> Boss (reaches the boss with no scoring needed; the mod's skip_blind hook).
                send(out, Map.of("type", "skipBlind", "seq", 2));
                assertThat(JSON.readTree(in.readLine()).path("accepted").asBoolean()).isTrue();
                send(out, Map.of("type", "skipBlind", "seq", 3));
                JsonNode v3 = JSON.readTree(in.readLine()).path("view");

                assertThat(v3.path("blind").asText()).isEqualTo("Boss Blind");
                assertThat(v3.path("boss").asText()).isNotEmpty();
                // The native key the bridge feeds to G.P_BLINDS[...].
                assertThat(v3.path("bossKey").asText()).startsWith("bl_");
            }
        }
    }

    private void send(BufferedWriter out, Map<String, Object> msg) throws Exception {
        out.write(JSON.writeValueAsString(msg));
        out.write('\n');
        out.flush();
    }

    private String login(int port, String username) throws Exception {
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/login"))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + username + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(resp.body()).path("token").asText();
    }
}
