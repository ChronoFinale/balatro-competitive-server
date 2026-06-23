package com.balatro.engine.joker.def;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.net.GameServer;
import com.balatro.engine.state.Ruleset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The joker-builder HTTP surface end-to-end: fetch the building-block schema,
 * POST a JokerDef, see it listed, and confirm a malformed def is rejected with a
 * 400. The created joker is registered server-side through the normal path.
 */
class BuilderApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void schemaCreateAndList(@TempDir Path dir) throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard(), new CustomJokerStore(dir)).start(0)) {
            int port = server.port();

            JsonNode schema = JSON.readTree(get(port, "/jokers/schema"));
            assertThat(schema.path("triggers").isArray()).isTrue();
            assertThat(schema.path("conditionTypes").size()).isGreaterThan(10);
            assertThat(schema.path("enums").path("suit").size()).isEqualTo(4);

            JokerDef def = new JokerDef("j_custom_wire", "Wire Joker", "+11 Mult", "Common",
                    4, 0, 0, null, null, true,
                    java.util.List.of(new Rule(
                            com.balatro.engine.joker.Trigger.JOKER_MAIN, new Condition.Always(),
                            Effect.mult(new Value.Const(11)))));
            HttpResponse<String> created = post(port, "/jokers", JSON.writeValueAsString(def));
            assertThat(created.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(created.body()).path("key").asText()).isEqualTo("j_custom_wire");

            JsonNode list = JSON.readTree(get(port, "/jokers"));
            assertThat(list.toString()).contains("j_custom_wire");
        }
    }

    @Test
    void rejectsAMalformedDef(@TempDir Path dir) throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard(), new CustomJokerStore(dir)).start(0)) {
            HttpResponse<String> bad = post(server.port(), "/jokers", "{\"key\":\"bad key\",\"name\":\"x\"}");
            assertThat(bad.statusCode()).isEqualTo(400);
            assertThat(bad.body()).contains("error");
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
