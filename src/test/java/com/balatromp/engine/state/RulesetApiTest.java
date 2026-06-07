package com.balatromp.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.joker.def.CustomJokerStore;
import com.balatromp.engine.net.GameServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The ruleset HTTP surface: list curated, see available jokers, create a custom ruleset. */
class RulesetApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void listsCuratedAndCreatesCustom(@TempDir Path jokers, @TempDir Path rulesets) throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard(),
                new CustomJokerStore(jokers), new RulesetStore(rulesets)).start(0)) {
            int port = server.port();

            JsonNode list = JSON.readTree(get(port, "/rulesets"));
            assertThat(list.toString()).contains("Standard").contains("Blitz");

            JsonNode pool = JSON.readTree(get(port, "/jokers/available"));
            assertThat(pool.isArray()).isTrue();
            assertThat(pool.size()).isGreaterThan(5); // the curated built-ins

            String body = "{\"name\":\"Wire Format\",\"startingMoney\":4,\"hands\":3,\"discards\":2,"
                    + "\"handSize\":7,\"anteScaling\":1.0,\"winAnte\":8,"
                    + "\"blindBaseAmounts\":[300,800,2000,5000,11000,20000,35000,50000],"
                    + "\"jokerPool\":[\"j_joker\",\"j_hack\"]}";
            HttpResponse<String> created = post(port, "/rulesets", body);
            assertThat(created.statusCode()).isEqualTo(200);

            assertThat(get(port, "/rulesets")).contains("Wire Format");
        }
    }

    @Test
    void rejectsUnknownJokerInPool(@TempDir Path jokers, @TempDir Path rulesets) throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard(),
                new CustomJokerStore(jokers), new RulesetStore(rulesets)).start(0)) {
            String body = "{\"name\":\"Bad Format\",\"startingMoney\":4,\"hands\":4,\"discards\":3,"
                    + "\"handSize\":8,\"anteScaling\":1.0,\"winAnte\":8,"
                    + "\"blindBaseAmounts\":[300,800,2000,5000,11000,20000,35000,50000],"
                    + "\"jokerPool\":[\"j_bogus\"]}";
            HttpResponse<String> bad = post(server.port(), "/rulesets", body);
            assertThat(bad.statusCode()).isEqualTo(400);
        }
    }

    private String get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private HttpResponse<String> post(int port, String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
