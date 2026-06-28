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
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The ranked queue over raw TCP: two waiting players get paired into a match (its ruleset-agreement phase
 * opens for both), a lone player waits, and leaving the queue cancels the wait. (MMR-preference pairing is
 * unit-tested in {@link MatchmakerTest}; dev logins all start at the default MMR, so they pair on arrival.)
 */
class QueueMatchmakingTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void twoQueuedPlayersGetPairedIntoAMatch() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            try (Conn a = new Conn(server.tcpPort()); Conn b = new Conn(server.tcpPort())) {
                auth(a, login(server.port(), "alice"));
                auth(b, login(server.port(), "bob"));

                a.send(Map.of("type", "joinQueue", "seq", 1));
                assertThat(readUntil(a, "queued")).isNotNull(); // alone, waiting

                b.send(Map.of("type", "joinQueue", "seq", 1)); // close MMR -> pairs immediately
                // Both are dropped into the same Match, which opens ruleset agreement.
                assertThat(readUntil(a, "lobbyReady")).isNotNull();
                assertThat(readUntil(b, "lobbyReady")).isNotNull();
            }
        }
    }

    @Test
    void leavingTheQueueCancelsTheWait() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            try (Conn a = new Conn(server.tcpPort())) {
                auth(a, login(server.port(), "carol"));
                a.send(Map.of("type", "joinQueue", "seq", 1));
                assertThat(readUntil(a, "queued")).isNotNull();
                a.send(Map.of("type", "leaveQueue", "seq", 2));
                assertThat(readUntil(a, "leftQueue")).isNotNull();
            }
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

    private static void auth(Conn c, String token) throws Exception {
        c.send(Map.of("type", "auth", "seq", 0, "token", token));
        assertThat(readUntil(c, "authed").path("type").asText()).isEqualTo("authed");
    }

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
