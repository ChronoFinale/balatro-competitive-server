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
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * End-to-end over a real WebSocket with JWT auth: HTTP login -> token -> auth on
 * the socket -> play. Validates the full wire path and the auth gate, and that
 * the server computes the score authoritatively (no client-supplied score).
 */
class NetworkTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void authenticatedPlayOverTheWire() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0)) {
            int port = server.port();
            String token = login(port, "alice");
            BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
            WebSocket ws = connect(port, inbox);

            ws.sendText(JSON.writeValueAsString(Map.of("type", "auth", "seq", 0, "token", token)), true).join();
            assertThat(JSON.readTree(await(inbox)).path("type").asText()).isEqualTo("authed");

            ws.sendText(JSON.writeValueAsString(Map.of("type", "newRun", "seq", 1, "seed", "NET")), true).join();
            JsonNode start = JSON.readTree(await(inbox));
            assertThat(start.path("view").path("ante").asInt()).isEqualTo(1);
            assertThat(start.path("view").path("requirement").asLong()).isEqualTo(300);

            ws.sendText(JSON.writeValueAsString(
                    Map.of("type", "playHand", "seq", 2, "cards", List.of(0, 1, 2, 3, 4))), true).join();
            JsonNode update = JSON.readTree(await(inbox));
            assertThat(update.path("accepted").asBoolean()).isTrue();
            assertThat(update.path("replay").size()).isGreaterThan(0);

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    @Test
    void rejectsUnauthenticatedIntents() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0)) {
            BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
            WebSocket ws = connect(server.port(), inbox);

            ws.sendText(JSON.writeValueAsString(Map.of("type", "newRun", "seq", 1)), true).join();
            JsonNode resp = JSON.readTree(await(inbox));
            assertThat(resp.path("type").asText()).isEqualTo("error");
            assertThat(resp.path("rejection").asText()).isEqualTo("unauthenticated");

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

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

    private static String await(BlockingQueue<String> inbox) throws InterruptedException {
        String msg = inbox.poll(5, TimeUnit.SECONDS);
        assertThat(msg).as("server response within 5s").isNotNull();
        return msg;
    }
}
