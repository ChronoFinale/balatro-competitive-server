package com.balatromp.engine.net;

import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.state.Ruleset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transport adapter: WebSocket (Javalin/Jetty) + JSON (Jackson) + JWT auth,
 * wrapped around the authoritative engine. No game logic lives here — it
 * authenticates the connection, parses an {@link Intent}, runs it through that
 * player's {@link Run}, and serializes the {@link ServerUpdate}.
 *
 * Flow: HTTP POST /login -> token. WS /game: first message must be
 * {@code {"type":"auth","token":...}}; only then are game intents accepted.
 * There is no message type that carries a client-supplied score.
 */
public final class GameServer implements AutoCloseable {

    private final Javalin app;
    private final Ruleset ruleset;
    private final AuthService auth = new AuthService();
    private final ObjectMapper json = new ObjectMapper();

    private final Map<String, String> players = new ConcurrentHashMap<>(); // sessionId -> playerId
    private final Map<String, Run> runs = new ConcurrentHashMap<>();       // sessionId -> run

    public GameServer(Ruleset ruleset) {
        this.ruleset = ruleset;
        this.app = Javalin.create(cfg -> cfg.showJavalinBanner = false);

        // --- auth: issue a session token (dev: any username; later: Steam ticket) ---
        app.post("/login", ctx -> {
            JsonNode body = json.readTree(ctx.body());
            String username = body.path("username").asText("");
            if (username.isBlank()) {
                ctx.status(400).json(Map.of("error", "username required"));
                return;
            }
            ctx.json(Map.of("token", auth.issue(username), "playerId", username));
        });

        // --- game socket ---
        app.ws("/game", ws -> {
            ws.onMessage(this::onMessage);
            ws.onClose(ctx -> {
                players.remove(ctx.sessionId());
                runs.remove(ctx.sessionId());
            });
        });
    }

    public GameServer start(int port) {
        app.start("127.0.0.1", port);
        return this;
    }

    public int port() {
        return app.port();
    }

    @Override
    public void close() {
        app.stop();
    }

    private void onMessage(WsMessageContext ctx) {
        try {
            JsonNode msg = json.readTree(ctx.message());
            String type = msg.path("type").asText();
            long seq = msg.path("seq").asLong();

            if (type.equals("auth")) {
                String playerId = auth.verify(msg.path("token").asText());
                if (playerId == null) {
                    respond(ctx, error(seq, "invalid token"));
                    return;
                }
                players.put(ctx.sessionId(), playerId);
                respond(ctx, Map.of("type", "authed", "seq", seq, "playerId", playerId));
                return;
            }

            if (!players.containsKey(ctx.sessionId())) {
                respond(ctx, error(seq, "unauthenticated"));
                return;
            }

            switch (type) {
                case "newRun" -> {
                    Run run = new Run(ruleset, msg.path("seed").asText("SEED"));
                    runs.put(ctx.sessionId(), run);
                    respond(ctx, ok(seq, run));
                }
                case "playHand" -> apply(ctx, seq, new Intent.PlayHand(ints(msg.path("cards"))));
                case "discard" -> apply(ctx, seq, new Intent.Discard(ints(msg.path("cards"))));
                case "proceed" -> {
                    Run run = runs.get(ctx.sessionId());
                    if (run == null) respond(ctx, error(seq, "no active run"));
                    else {
                        run.proceed();
                        respond(ctx, ok(seq, run));
                    }
                }
                default -> respond(ctx, error(seq, "unknown type: " + type));
            }
        } catch (Exception e) {
            respond(ctx, error(0, "bad message: " + e.getMessage()));
        }
    }

    private void apply(WsMessageContext ctx, long seq, Intent intent) {
        Run run = runs.get(ctx.sessionId());
        if (run == null) {
            respond(ctx, error(seq, "no active run"));
            return;
        }
        ServerUpdate up = run.submit(intent);
        respond(ctx, new WsResponse("update", seq, up.accepted(), up.rejection(), up.view(), up.replay()));
    }

    private WsResponse ok(long seq, Run run) {
        return new WsResponse("update", seq, true, null, run.view(), List.of());
    }

    private WsResponse error(long seq, String message) {
        return new WsResponse("error", seq, false, message, null, List.of());
    }

    private void respond(WsContext ctx, Object payload) {
        try {
            ctx.send(json.writeValueAsString(payload));
        } catch (Exception ignored) {
            // connection went away; authoritative state is untouched
        }
    }

    private static List<Integer> ints(JsonNode arr) {
        List<Integer> r = new ArrayList<>();
        if (arr.isArray()) arr.forEach(n -> r.add(n.asInt()));
        return r;
    }

    /** The server -> client message envelope. */
    public record WsResponse(
            String type,
            long seq,
            boolean accepted,
            String rejection,
            ClientView view,
            List<com.balatromp.engine.scoring.ReplayEntry> replay) {
    }
}
