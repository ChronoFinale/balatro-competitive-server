package com.balatromp.engine.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatromp.engine.net.GameServer;
import com.balatromp.engine.state.Ruleset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The OAuth HTTP surface: list providers, log in via the mock provider, get a reusable token + account. */
class AuthApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String USER = "e2e_login_user";
    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(Path.of("web-assets/accounts/mock_" + USER + ".json"));
    }

    @Test
    void mockLoginIssuesReusableTokenAndAccount() throws Exception {
        try (GameServer server = new GameServer(Ruleset.standard()).start(0)) {
            int port = server.port();

            assertThat(get(port, "/auth/providers")).contains("mock");

            // log in via the mock provider (code == chosen identity)
            JsonNode login = JSON.readTree(get(port, "/auth/mock/callback?code=" + USER));
            String token = login.path("token").asText();
            assertThat(token).isNotEmpty();
            assertThat(login.path("accountId").asText()).isEqualTo("mock:" + USER);
            assertThat(login.path("name").asText()).isEqualTo(USER);

            // reuse the stored token: /auth/me validates it and returns the account
            JsonNode me = JSON.readTree(get(port, "/auth/me?token=" + token));
            assertThat(me.path("accountId").asText()).isEqualTo("mock:" + USER);

            // a tampered token is rejected
            assertThat(getStatus(port, "/auth/me?token=garbage")).isEqualTo(401);
        }
    }

    private String get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private int getStatus(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
