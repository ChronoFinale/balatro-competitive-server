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
 * Session lifecycle over raw TCP: heartbeat ping/pong, and the identity-owned game
 * state surviving a disconnect — a player who re-auths within the grace window resumes
 * their run, but loses it once the grace timer expires.
 */
class SessionLifecycleTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void heartbeatPingGetsPong() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            String token = login(server.port(), "ping-user");
            try (Conn c = new Conn(server.tcpPort())) {
                c.send(Map.of("type", "auth", "seq", 0, "token", token));
                assertThat(JSON.readTree(c.read()).path("type").asText()).isEqualTo("authed");
                c.send(Map.of("type", "ping", "seq", 7));
                JsonNode pong = JSON.readTree(c.read());
                assertThat(pong.path("type").asText()).isEqualTo("pong");
                assertThat(pong.path("seq").asLong()).isEqualTo(7);
            }
        }
    }

    @Test
    void reconnectWithinGraceResumesTheRun() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            server.setGraceSeconds(30); // generous; we reconnect immediately
            String token = login(server.port(), "dave");

            // First connection: start a run, then drop the socket.
            try (Conn c1 = new Conn(server.tcpPort())) {
                c1.send(Map.of("type", "auth", "seq", 0, "token", token));
                assertThat(JSON.readTree(c1.read()).path("type").asText()).isEqualTo("authed");
                c1.send(Map.of("type", "newRun", "seq", 1, "seed", "RESUME"));
                assertThat(JSON.readTree(c1.read()).path("view").path("ante").asInt()).isEqualTo(1);
            } // socket closed -> server starts the grace window, keeps the run

            // Reconnect as the same identity: auth, then an automatic resume view.
            try (Conn c2 = new Conn(server.tcpPort())) {
                c2.send(Map.of("type", "auth", "seq", 0, "token", token));
                assertThat(JSON.readTree(c2.read()).path("type").asText()).isEqualTo("authed");
                JsonNode resume = JSON.readTree(c2.read()); // resent view, no newRun needed
                assertThat(resume.path("type").asText()).isEqualTo("update");
                assertThat(resume.path("view").path("ante").asInt()).isEqualTo(1);

                // And the run is live: a hand plays without re-creating it.
                c2.send(Map.of("type", "playHand", "seq", 1, "cards", List.of(0, 1, 2, 3, 4)));
                assertThat(JSON.readTree(c2.read()).path("accepted").asBoolean()).isTrue();
            }
        }
    }

    @Test
    void runIsDroppedAfterGraceExpires() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            server.setGraceSeconds(1); // expire quickly
            String token = login(server.port(), "erin");

            try (Conn c1 = new Conn(server.tcpPort())) {
                c1.send(Map.of("type", "auth", "seq", 0, "token", token));
                JSON.readTree(c1.read());
                c1.send(Map.of("type", "newRun", "seq", 1, "seed", "GONE"));
                JSON.readTree(c1.read());
            }

            Thread.sleep(2500); // grace (1s) elapses -> run cleaned up

            try (Conn c2 = new Conn(server.tcpPort())) {
                c2.send(Map.of("type", "auth", "seq", 0, "token", token));
                assertThat(JSON.readTree(c2.read()).path("type").asText()).isEqualTo("authed");
                // No resume view this time; the run is gone, so an action is rejected.
                c2.send(Map.of("type", "playHand", "seq", 1, "cards", List.of(0, 1, 2, 3, 4)));
                JsonNode resp = JSON.readTree(c2.read());
                assertThat(resp.path("type").asText()).isEqualTo("error");
                assertThat(resp.path("rejection").asText()).isEqualTo("no active run");
            }
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

    /** Minimal newline-delimited JSON socket client. */
    private static final class Conn implements AutoCloseable {
        private final Socket socket = new Socket();
        private final BufferedWriter out;
        private final BufferedReader in;

        Conn(int tcpPort) throws Exception {
            socket.connect(new InetSocketAddress("127.0.0.1", tcpPort), 2000);
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
