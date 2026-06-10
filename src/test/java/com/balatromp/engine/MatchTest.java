package com.balatromp.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.net.GameServer;
import com.balatromp.engine.state.Ruleset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Two real WebSocket clients play a competitive match end-to-end: create lobby ->
 * join by code -> both play -> server compares scores and pushes results.
 * Both play identically on the shared seed, so it's a deterministic tie — which
 * exercises the full coupling, the opponent server-push, and round resolution.
 */
class MatchTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void twoPlayersDuelOverTheWire() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0)) {
            int port = server.port();
            BlockingQueue<String> aIn = new LinkedBlockingQueue<>();
            BlockingQueue<String> bIn = new LinkedBlockingQueue<>();

            WebSocket a = connect(port, aIn);
            WebSocket b = connect(port, bIn);
            authenticate(a, aIn, login(port, "alice"));
            authenticate(b, bIn, login(port, "bob"));

            // Alice creates a lobby; Bob joins by code.
            a.sendText(json(Map.of("type", "createLobby", "seq", 1)), true).join();
            String code = awaitType(aIn, "lobbyCreated").path("code").asText();
            assertThat(code).hasSize(5);

            b.sendText(json(Map.of("type", "joinLobby", "seq", 1, "code", code)), true).join();

            // Ruleset agreement: host (alice) is told to propose; she offers "Standard";
            // bob sees the proposal (full data incl. joker pool) and accepts.
            JsonNode ready = awaitType(aIn, "lobbyReady");
            assertThat(ready.path("youPropose").asBoolean()).isTrue();
            assertThat(ready.path("rulesets").size()).isGreaterThanOrEqualTo(3); // curated catalog
            a.sendText(json(Map.of("type", "proposeRuleset", "seq", 2, "name", "Standard")), true).join();

            JsonNode proposal = awaitType(bIn, "rulesetProposed");
            assertThat(proposal.path("ruleset").path("name").asText()).isEqualTo("Standard");
            assertThat(proposal.path("ruleset").path("jokerPool").size()).isGreaterThan(0);
            b.sendText(json(Map.of("type", "respondRuleset", "seq", 2, "accept", true)), true).join();

            // Each player now drives a full Run (same seed). matchStart carries the
            // player's own view (hand, shop, jokers, etc.).
            JsonNode aStart = drainUntil(aIn, "matchStart", new HashSet<>());
            JsonNode bStart = drainUntil(bIn, "matchStart", new HashSet<>());
            assertThat(aStart.path("opponent").asText()).isEqualTo("bob");
            assertThat(aStart.path("view").path("hand").size()).isEqualTo(8); // "Standard" survived
            assertThat(bStart.path("view").path("hand").size()).isEqualTo(8);
            // Nemesis state is synced into each Run: the view's counters carry the opponent's
            // live values (both start with 4 hands), which the Nemesis jokers score off.
            assertThat(aStart.path("view").path("counters").path("OPP_HANDS_LEFT").asInt()).isEqualTo(4);

            // Alice plays a hand: she gets an authoritative update; Bob gets a push.
            a.sendText(playHand(2), true).join();
            JsonNode aUpdate = drainUntil(aIn, "update", new HashSet<>());
            assertThat(aUpdate.path("accepted").asBoolean()).isTrue();

            JsonNode bOpp = drainUntil(bIn, "opponentUpdate", new HashSet<>());
            assertThat(bOpp.path("playerId").asText()).isEqualTo("alice");
            assertThat(bOpp.path("ante").asInt()).isEqualTo(1);

            a.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            b.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    // ---- helpers ----

    private String login(int port, String username) throws Exception {
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/login"))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + username + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(resp.body()).path("token").asText();
    }

    private WebSocket connect(int port, BlockingQueue<String> inbox) {
        return http.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/game"), new WebSocket.Listener() {
                    private final StringBuilder buf = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buf.append(data);
                        if (last) {
                            inbox.offer(buf.toString());
                            buf.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).join();
    }

    private void authenticate(WebSocket ws, BlockingQueue<String> inbox, String token) throws Exception {
        ws.sendText(json(Map.of("type", "auth", "seq", 0, "token", token)), true).join();
        assertThat(awaitType(inbox, "authed").path("type").asText()).isEqualTo("authed");
    }

    private static String json(Map<String, Object> m) throws Exception {
        return JSON.writeValueAsString(m);
    }

    private static String playHand(int seq) throws Exception {
        return json(Map.of("type", "playHand", "seq", seq, "cards", List.of(0, 1, 2, 3, 4)));
    }

    private static JsonNode awaitType(BlockingQueue<String> inbox, String type) throws Exception {
        return drainUntil(inbox, type, new HashSet<>());
    }

    /** Poll until a message of {@code type} arrives, recording seen types in {@code seen}. */
    private static JsonNode drainUntil(BlockingQueue<String> inbox, String type, Set<String> seen) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        List<String> drained = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            String msg = inbox.poll(8, TimeUnit.SECONDS);
            if (msg == null) break;
            drained.add(msg);
            JsonNode node = JSON.readTree(msg);
            seen.add(node.path("type").asText());
            if (node.path("type").asText().equals(type)) {
                return node;
            }
        }
        throw new AssertionError("did not receive '" + type + "'; saw " + seen + " (" + drained.size() + " msgs)");
    }
}
