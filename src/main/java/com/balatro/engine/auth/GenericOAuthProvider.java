package com.balatro.engine.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A real OAuth 2.0 (authorization-code) provider driven entirely by config —
 * works for Google, Discord, GitHub, etc. via a {@link Preset}. Activated only
 * when its client id/secret are present (see {@link #fromEnv}); without
 * credentials the provider simply isn't registered, so dev runs use the mock.
 *
 * <p>This is the production path: build the provider authorize URL, then exchange
 * the code at the token endpoint and read the user from the userinfo endpoint.
 */
public final class GenericOAuthProvider implements OAuthProvider {

    /** Endpoints + identity field names for a provider. */
    public record Preset(String name, String authUrl, String tokenUrl, String userInfoUrl,
                         String scope, String idField, String nameField) {}

    public static final Preset GOOGLE = new Preset("google",
            "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token",
            "https://www.googleapis.com/oauth2/v3/userinfo", "openid email profile", "sub", "name");
    public static final Preset DISCORD = new Preset("discord",
            "https://discord.com/oauth2/authorize", "https://discord.com/api/oauth2/token",
            "https://discord.com/api/users/@me", "identify", "id", "username");

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final Preset preset;
    private final String clientId;
    private final String clientSecret;

    public GenericOAuthProvider(Preset preset, String clientId, String clientSecret) {
        this.preset = preset;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Build a provider from {@code <NAME>_CLIENT_ID}/{@code _CLIENT_SECRET} env vars, if set. */
    public static Optional<GenericOAuthProvider> fromEnv(Preset preset) {
        String id = System.getenv(preset.name().toUpperCase() + "_CLIENT_ID");
        String secret = System.getenv(preset.name().toUpperCase() + "_CLIENT_SECRET");
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.of(new GenericOAuthProvider(preset, id, secret));
    }

    @Override
    public String name() {
        return preset.name();
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        return preset.authUrl()
                + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&response_type=code"
                + "&scope=" + enc(preset.scope())
                + "&state=" + enc(state);
    }

    @Override
    public Identity exchange(String code, String redirectUri) throws Exception {
        String body = "client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri);
        HttpResponse<String> tokenResp = HTTP.send(HttpRequest.newBuilder(URI.create(preset.tokenUrl()))
                .header("content-type", "application/x-www-form-urlencoded")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        String accessToken = json.readTree(tokenResp.body()).path("access_token").asText();
        if (accessToken.isEmpty()) throw new IllegalStateException("no access_token from " + preset.name());

        HttpResponse<String> userResp = HTTP.send(HttpRequest.newBuilder(URI.create(preset.userInfoUrl()))
                .header("authorization", "Bearer " + accessToken)
                .header("accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonNode user = json.readTree(userResp.body());
        String uid = user.path(preset.idField()).asText();
        String name = user.path(preset.nameField()).asText(uid);
        if (uid.isEmpty()) throw new IllegalStateException("no user id from " + preset.name());
        return new Identity(uid, name);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
