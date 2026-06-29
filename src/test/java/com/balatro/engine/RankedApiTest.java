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

/** The ranked HTTP/auth surface: the /leaderboard endpoint serves a JSON array, and the authed reply
 *  carries the player's MMR + derived tier (Unranked until they've played). */
class RankedApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void leaderboardServesAJsonArray() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0)) {
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + server.port() + "/leaderboard?limit=5")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(resp.body()).isArray()).isTrue();
        }
    }

    @Test
    void rankEndpointDefaultsForAnUnknownPlayer() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0)) {
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + server.port() + "/rank/nobody_ranked_probe")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(resp.statusCode()).isEqualTo(200);
            JsonNode rank = JSON.readTree(resp.body());
            assertThat(rank.path("mmr").asInt()).isEqualTo(1000);
            assertThat(rank.path("rank").asText()).isEqualTo("Unranked");
            assertThat(rank.path("gamesPlayed").asInt()).isZero();
            assertThat(rank.path("position").asInt()).isZero();
        }
    }

    @Test
    void authedReplyCarriesMmrAndUnrankedTierForAFreshPlayer() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0).startTcp(0)) {
            String token = login(server.port(), "ranked_probe_user"); // never played -> no account
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", server.tcpPort()), 2000);
                s.setSoTimeout(8000);
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                out.write(JSON.writeValueAsString(Map.of("type", "auth", "seq", 0, "token", token)));
                out.write('\n');
                out.flush();
                JsonNode authed = JSON.readTree(in.readLine());
                assertThat(authed.path("type").asText()).isEqualTo("authed");
                assertThat(authed.path("mmr").asInt()).isEqualTo(1000);
                assertThat(authed.path("rank").asText()).isEqualTo("Unranked");
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
}
