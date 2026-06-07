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
 * The transport adapter: WebSocket (Javalin/Jetty) + JSON (Jackson) wrapped
 * around the authoritative engine. It does no game logic — it parses an
 * {@link Intent}, runs it through that connection's {@link Run}, and serializes
 * the {@link ServerUpdate}. The wire protocol is a versionable JSON envelope;
 * there is, by design, no message type that carries a client-supplied score.
 *
 * One {@link Run} per connection for now (single-player); multiplayer match
 * coupling slots in here next.
 */
public final class GameServer implements AutoCloseable {

    private final Javalin app;
    private final Ruleset ruleset;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Run> runs = new ConcurrentHashMap<>();

    public GameServer(Ruleset ruleset) {
        this.ruleset = ruleset;
        this.app = Javalin.create(cfg -> cfg.showJavalinBanner = false);
        app.ws("/game", ws -> {
            ws.onMessage(this::onMessage);
            ws.onClose(ctx -> runs.remove(ctx.sessionId()));
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
            switch (type) {
                case "newRun" -> {
                    Run run = new Run(ruleset, msg.path("seed").asText("SEED"));
                    runs.put(ctx.sessionId(), run);
                    send(ctx, ok(seq, run));
                }
                case "playHand" -> apply(ctx, seq, new Intent.PlayHand(ints(msg.path("cards"))));
                case "discard" -> apply(ctx, seq, new Intent.Discard(ints(msg.path("cards"))));
                case "proceed" -> {
                    Run run = runs.get(ctx.sessionId());
                    if (run == null) send(ctx, error(seq, "no active run"));
                    else {
                        run.proceed();
                        send(ctx, ok(seq, run));
                    }
                }
                default -> send(ctx, error(seq, "unknown type: " + type));
            }
        } catch (Exception e) {
            send(ctx, error(0, "bad message: " + e.getMessage()));
        }
    }

    private void apply(WsMessageContext ctx, long seq, Intent intent) {
        Run run = runs.get(ctx.sessionId());
        if (run == null) {
            send(ctx, error(seq, "no active run"));
            return;
        }
        ServerUpdate up = run.submit(intent);
        send(ctx, new WsResponse("update", seq, up.accepted(), up.rejection(), up.view(), up.replay()));
    }

    private WsResponse ok(long seq, Run run) {
        return new WsResponse("update", seq, true, null, run.view(), List.of());
    }

    private WsResponse error(long seq, String message) {
        return new WsResponse("error", seq, false, message, null, List.of());
    }

    private void send(WsContext ctx, WsResponse response) {
        try {
            ctx.send(json.writeValueAsString(response));
        } catch (Exception ignored) {
            // connection went away; the engine state is untouched
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
