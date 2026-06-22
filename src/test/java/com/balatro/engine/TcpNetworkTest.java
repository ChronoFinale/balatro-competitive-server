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
 * The same JSON protocol as {@link NetworkTest}, but over the RAW TCP transport
 * (newline-delimited messages) — the wire the Balatro Lua mod and the Electron
 * reference client speak. HTTP login still issues the token; auth + intents flow
 * over the socket; the server scores authoritatively.
 */
class TcpNetworkTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void authenticatedPlayOverRawTcp() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            String token = login(server.port(), "carol");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", server.tcpPort()), 2000);
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                send(out, Map.of("type", "auth", "seq", 0, "token", token));
                JsonNode authed = JSON.readTree(in.readLine());
                assertThat(authed.path("type").asText()).isEqualTo("authed");
                // the server offers its rulesets (curated + bundles) so the client can select one
                JsonNode rulesets = authed.path("rulesets");
                assertThat(rulesets.isArray()).isTrue();
                assertThat(rulesets.toString()).contains("Standard").contains("vanilla-pvp");

                // newRun naming a ruleset the server resolves (here the bundle "vanilla-pvp")
                send(out, Map.of("type", "newRun", "seq", 1, "seed", "TCP", "ruleset", "vanilla-pvp"));
                JsonNode start = JSON.readTree(in.readLine());
                assertThat(start.path("view").path("ante").asInt()).isEqualTo(1);
                assertThat(start.path("view").path("requirement").asLong()).isEqualTo(300);

                send(out, Map.of("type", "playHand", "seq", 2, "cards", List.of(0, 1, 2, 3, 4)));
                JsonNode update = JSON.readTree(in.readLine());
                assertThat(update.path("accepted").asBoolean()).isTrue();
                assertThat(update.path("replay").size()).isGreaterThan(0);
            }
        }
    }

    @Test
    void rejectsUnauthenticatedIntentsOverRawTcp() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", server.tcpPort()), 2000);
                BufferedWriter out = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                send(out, Map.of("type", "newRun", "seq", 1));
                JsonNode resp = JSON.readTree(in.readLine());
                assertThat(resp.path("type").asText()).isEqualTo("error");
                assertThat(resp.path("rejection").asText()).isEqualTo("unauthenticated");
            }
        }
    }

    @Test
    void matchQueueAcceptsAndPairsPlayers() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            try (Socket s1 = new Socket(); Socket s2 = new Socket()) {
                s1.connect(new InetSocketAddress("127.0.0.1", server.tcpPort()), 2000);
                s2.connect(new InetSocketAddress("127.0.0.1", server.tcpPort()), 2000);
                var o1 = new BufferedWriter(new OutputStreamWriter(s1.getOutputStream(), StandardCharsets.UTF_8));
                var i1 = new BufferedReader(new InputStreamReader(s1.getInputStream(), StandardCharsets.UTF_8));
                var o2 = new BufferedWriter(new OutputStreamWriter(s2.getOutputStream(), StandardCharsets.UTF_8));
                var i2 = new BufferedReader(new InputStreamReader(s2.getInputStream(), StandardCharsets.UTF_8));

                send(o1, Map.of("type", "auth", "seq", 0, "token", login(server.port(), "p1")));
                assertThat(JSON.readTree(i1.readLine()).path("type").asText()).isEqualTo("authed");
                send(o2, Map.of("type", "auth", "seq", 0, "token", login(server.port(), "p2")));
                assertThat(JSON.readTree(i2.readLine()).path("type").asText()).isEqualTo("authed");

                // p1 joins -> waits; p2 joins -> the two are paired into a match (agreement pushed to p2)
                send(o1, Map.of("type", "joinQueue", "seq", 1));
                assertThat(JSON.readTree(i1.readLine()).path("type").asText()).isEqualTo("queued");
                send(o2, Map.of("type", "joinQueue", "seq", 1));
                assertThat(JSON.readTree(i2.readLine()).path("type").asText()).isNotEqualTo("error"); // matched
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
