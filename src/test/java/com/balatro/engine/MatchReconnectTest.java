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
 * R2 — reconnect to a LIVE match over raw TCP. A match keeps running for the opponent while a player is
 * dropped; the dropped player's identity (playerId) owns their Side, so on re-auth within the grace window
 * they re-attach to the same Match (new socket, same game) and get their match start + view re-pushed. If
 * the grace window elapses without a reconnect, the match is forfeit and the opponent wins.
 */
class MatchReconnectTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @org.junit.jupiter.api.AfterEach
    void cleanup() throws Exception {
        // The forfeit path feeds the ranked ladder, persisting these dev accounts (default dir).
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("web-assets/accounts/alice.json"));
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("web-assets/accounts/bob.json"));
    }

    @Test
    void reconnectWithinGraceReattachesToTheLiveMatch() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            server.setGraceSeconds(30);
            String aTok = login(server.port(), "alice");
            String bTok = login(server.port(), "bob");

            try (Conn a = new Conn(server.tcpPort()); Conn b1 = new Conn(server.tcpPort())) {
                auth(a, aTok);
                auth(b1, bTok);
                startMatch(a, b1); // -> both PLAYING

                // Bob drops his socket; the match keeps running for Alice.
                b1.close();

                // Bob reconnects as the same identity: authed, then an auto re-pushed matchStart.
                try (Conn b2 = new Conn(server.tcpPort())) {
                    auth(b2, bTok);
                    JsonNode restart = readUntil(b2, "matchStart");
                    assertThat(restart.path("opponent").asText()).isEqualTo("alice");
                    assertThat(restart.path("view").path("hand").size()).isEqualTo(8);

                    // And he's live: a hand plays on the re-attached run.
                    b2.send(Map.of("type", "playHand", "seq", 9, "cards", List.of(0, 1, 2, 3, 4)));
                    assertThat(readUntil(b2, "update").path("accepted").asBoolean()).isTrue();
                }
            }
        }
    }

    @Test
    void abandoningPastGraceForfeitsTheMatchToTheOpponent() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            server.setGraceSeconds(1); // expire quickly
            String aTok = login(server.port(), "alice");
            String bTok = login(server.port(), "bob");

            try (Conn a = new Conn(server.tcpPort())) {
                Conn b = new Conn(server.tcpPort());
                auth(a, aTok);
                auth(b, bTok);
                startMatch(a, b);

                b.close(); // Bob abandons and never comes back

                // After the grace window, the match forfeits to Alice.
                JsonNode result = readUntil(a, "matchResult");
                assertThat(result.path("winner").asText()).isEqualTo("alice");
            }
        }
    }

    // ---- match setup over TCP (lobby -> agreement -> playing) ----

    private void startMatch(Conn a, Conn b) throws Exception {
        a.send(Map.of("type", "createLobby", "seq", 1));
        String code = readUntil(a, "lobbyCreated").path("code").asText();
        b.send(Map.of("type", "joinLobby", "seq", 1, "code", code));
        readUntil(a, "lobbyReady"); // host is told to propose
        a.send(Map.of("type", "proposeRuleset", "seq", 2, "name", "Standard"));
        readUntil(b, "rulesetProposed");
        b.send(Map.of("type", "respondRuleset", "seq", 2, "accept", true));
        readUntil(a, "matchStart");
        readUntil(b, "matchStart");
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

    private static void auth(Conn c, String token) throws Exception {
        c.send(Map.of("type", "auth", "seq", 0, "token", token));
        assertThat(readUntil(c, "authed").path("type").asText()).isEqualTo("authed");
    }

    /** Read newline-delimited messages until one of {@code type} arrives (8s budget). */
    private static JsonNode readUntil(Conn c, String type) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        StringBuilder seen = new StringBuilder();
        while (System.nanoTime() < deadline) {
            String line = c.read();
            if (line == null) break;
            JsonNode node = JSON.readTree(line);
            seen.append(node.path("type").asText()).append(' ');
            if (node.path("type").asText().equals(type)) return node;
        }
        throw new AssertionError("did not receive '" + type + "'; saw " + seen);
    }

    /** Minimal newline-delimited JSON socket client with a read timeout. */
    private static final class Conn implements AutoCloseable {
        private final Socket socket = new Socket();
        private final BufferedWriter out;
        private final BufferedReader in;

        Conn(int tcpPort) throws Exception {
            socket.connect(new InetSocketAddress("127.0.0.1", tcpPort), 2000);
            socket.setSoTimeout(9000);
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }

        void send(Map<String, Object> msg) throws Exception {
            out.write(JSON.writeValueAsString(msg));
            out.write('\n');
            out.flush();
        }

        String read() throws Exception {
            return in.readLine();
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
