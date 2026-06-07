package com.balatromp.engine.net;

import com.balatromp.engine.game.Match;
import com.balatromp.engine.game.Run;
import com.balatromp.engine.intent.Intent;
import com.balatromp.engine.joker.def.BuilderSchema;
import com.balatromp.engine.joker.def.CustomJokerStore;
import com.balatromp.engine.joker.def.JokerDef;
import com.balatromp.engine.state.Ruleset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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
    private final CustomJokerStore jokerStore;
    private final AuthService auth = new AuthService();
    private final ObjectMapper json = new ObjectMapper();

    private final Map<String, String> players = new ConcurrentHashMap<>();   // sessionId -> playerId
    private final Map<String, WsContext> ctxs = new ConcurrentHashMap<>();   // sessionId -> socket (for push)
    private final Map<String, Run> runs = new ConcurrentHashMap<>();         // sessionId -> solo run
    private final Map<String, Match> matchBySession = new ConcurrentHashMap<>();
    private final Map<String, Match> pendingByCode = new ConcurrentHashMap<>();
    private final Set<String> activeCodes = ConcurrentHashMap.newKeySet();

    public GameServer(Ruleset ruleset) {
        this(ruleset, new CustomJokerStore(
                new java.io.File("web-assets/custom-jokers").getAbsoluteFile().toPath()));
    }

    public GameServer(Ruleset ruleset, CustomJokerStore jokerStore) {
        this.ruleset = ruleset;
        this.jokerStore = jokerStore;
        jokerStore.loadAll(); // register any previously-authored custom jokers
        // Javalin 7: routes/ws/static are configured upfront in the create() block.
        java.io.File assetsDir = new java.io.File("web-assets").getAbsoluteFile();
        this.app = Javalin.create(cfg -> {
            cfg.staticFiles.add("/public", Location.CLASSPATH); // the web client at "/"

            // Optional real card sprites: drop Balatro's atlases in ./web-assets
            // (git-ignored, not shipped). The client uses them if present, else CSS.
            cfg.routes.get("/assets/{name}", ctx -> {
                java.io.File f = new java.io.File(assetsDir, ctx.pathParam("name"));
                if (assetsDir.equals(f.getParentFile()) && f.isFile()) {
                    if (f.getName().endsWith(".webp")) ctx.contentType("image/webp");
                    else if (f.getName().endsWith(".png")) ctx.contentType("image/png");
                    ctx.result(java.nio.file.Files.readAllBytes(f.toPath()));
                } else {
                    ctx.status(404);
                }
            });

            // ---- Joker builder: define a joker as data, persist + register it ----

            // The building-block vocabulary the builder UI renders (driven by real enums).
            cfg.routes.get("/jokers/schema", ctx -> ctx.json(BuilderSchema.build()));

            // List every custom joker authored so far.
            cfg.routes.get("/jokers", ctx -> ctx.json(jokerStore.all()));

            // Create/replace a custom joker from a JokerDef JSON body.
            cfg.routes.post("/jokers", ctx -> {
                try {
                    JokerDef def = json.readValue(ctx.body(), JokerDef.class);
                    ctx.json(jokerStore.save(def));
                } catch (IllegalArgumentException e) {
                    ctx.status(400).json(Map.of("error", e.getMessage()));
                } catch (Exception e) {
                    ctx.status(400).json(Map.of("error", "invalid joker definition: " + e.getMessage()));
                }
            });

            // Upload a sprite (raw PNG body) for an existing joker; scale = 1 or 2.
            cfg.routes.post("/jokers/{key}/sprite/{scale}", ctx -> {
                try {
                    JokerDef updated = jokerStore.saveSprite(
                            ctx.pathParam("key"),
                            Integer.parseInt(ctx.pathParam("scale")),
                            ctx.bodyAsBytes());
                    ctx.json(updated);
                } catch (IllegalArgumentException e) { // incl. NumberFormatException
                    ctx.status(400).json(Map.of("error", String.valueOf(e.getMessage())));
                }
            });

            // Serve uploaded custom sprites (traversal-safe, under the store dir).
            cfg.routes.get("/custom/{name}", ctx -> {
                java.nio.file.Path f = jokerStore.resolveAsset(ctx.pathParam("name"));
                if (f != null && java.nio.file.Files.isRegularFile(f)) {
                    if (f.toString().endsWith(".png")) ctx.contentType("image/png");
                    ctx.result(java.nio.file.Files.readAllBytes(f));
                } else {
                    ctx.status(404);
                }
            });

            // Auth: issue a session token (dev: any username; later: Steam ticket).
            cfg.routes.post("/login", ctx -> {
                JsonNode body = json.readTree(ctx.body());
                String username = body.path("username").asText("");
                if (username.isBlank()) {
                    ctx.status(400).json(Map.of("error", "username required"));
                    return;
                }
                ctx.json(Map.of("token", auth.issue(username), "playerId", username));
            });

            // Game socket.
            cfg.routes.ws("/game", ws -> {
                ws.onMessage(this::onMessage);
                ws.onClose(ctx -> {
                    players.remove(ctx.sessionId());
                    ctxs.remove(ctx.sessionId());
                    runs.remove(ctx.sessionId());
                    matchBySession.remove(ctx.sessionId());
                });
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
                ctxs.put(ctx.sessionId(), ctx);
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
                case "createLobby" -> createLobby(ctx, seq);
                case "joinLobby" -> joinLobby(ctx, seq, msg.path("code").asText());
                case "ban" -> {
                    Match m = matchBySession.get(ctx.sessionId());
                    if (m != null) m.ban(ctx.sessionId(), msg.path("ruleset").asText());
                    else respond(ctx, error(seq, "not in a match"));
                }
                case "playHand" -> route(ctx, seq, new Intent.PlayHand(ints(msg.path("cards"))));
                case "discard" -> route(ctx, seq, new Intent.Discard(ints(msg.path("cards"))));
                case "buyJoker" -> {
                    int index = msg.path("index").asInt();
                    soloAction(ctx, seq, run -> run.buyJoker(index));
                }
                case "reroll" -> soloAction(ctx, seq, Run::reroll);
                case "buyPlanet" -> {
                    int index = msg.path("index").asInt();
                    soloAction(ctx, seq, run -> run.buyPlanet(index));
                }
                case "useConsumable" -> {
                    int index = msg.path("index").asInt();
                    soloAction(ctx, seq, run -> run.useConsumable(index));
                }
                case "proceed" -> soloAction(ctx, seq, run -> {
                    run.proceed();
                    return null;
                });
                default -> respond(ctx, error(seq, "unknown type: " + type));
            }
        } catch (Exception e) {
            respond(ctx, error(0, "bad message: " + e.getMessage()));
        }
    }

    /** The player's authoritative Run: their match's run if in a match, else solo. */
    private Run runFor(String sessionId) {
        Match m = matchBySession.get(sessionId);
        return (m != null) ? m.runOf(sessionId) : runs.get(sessionId);
    }

    /** After applying an action, let the match push opponent state + decide the match. */
    private void afterAction(String sessionId) {
        Match m = matchBySession.get(sessionId);
        if (m != null) m.onAction(sessionId);
    }

    /** Apply a Run action (shop/planet/proceed) and reply with the new view. */
    private void soloAction(WsMessageContext ctx, long seq, java.util.function.Function<Run, String> action) {
        Run run = runFor(ctx.sessionId());
        if (run == null) {
            respond(ctx, error(seq, "no active run"));
            return;
        }
        String err = action.apply(run);
        respond(ctx, new WsResponse("update", seq, err == null, err, run.view(), List.of()));
        afterAction(ctx.sessionId());
    }

    /** Route a play/discard to the player's Run (match or solo). */
    private void route(WsMessageContext ctx, long seq, Intent intent) {
        Run run = runFor(ctx.sessionId());
        if (run == null) {
            respond(ctx, error(seq, "no active run"));
            return;
        }
        ServerUpdate up = run.submit(intent);
        respond(ctx, new WsResponse("update", seq, up.accepted(), up.rejection(), up.view(), up.replay()));
        afterAction(ctx.sessionId());
    }

    private void createLobby(WsMessageContext ctx, long seq) {
        String code = newCode();
        String seed = Long.toHexString(System.nanoTime()) + code;
        Match match = new Match(code, seed, ruleset, this::deliver);
        match.setHost(ctx.sessionId(), players.get(ctx.sessionId()));
        pendingByCode.put(code, match);
        matchBySession.put(ctx.sessionId(), match);
        respond(ctx, Map.of("type", "lobbyCreated", "seq", seq, "code", code));
    }

    private void joinLobby(WsMessageContext ctx, long seq, String code) {
        Match match = pendingByCode.remove(code);
        if (match == null) {
            respond(ctx, error(seq, "no such lobby: " + code));
            return;
        }
        matchBySession.put(ctx.sessionId(), match);
        match.setGuestAndStart(ctx.sessionId(), players.get(ctx.sessionId())); // pushes matchStart to both
    }

    /** Transport sink the Match uses to push to either player (incl. the opponent). */
    private void deliver(String sessionId, Object payload) {
        WsContext c = ctxs.get(sessionId);
        if (c == null) return;
        try {
            c.send(json.writeValueAsString(payload));
        } catch (Exception ignored) {
        }
    }

    private String newCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        String code;
        do {
            StringBuilder sb = new StringBuilder(5);
            for (int i = 0; i < 5; i++) {
                sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
            }
            code = sb.toString();
        } while (!activeCodes.add(code));
        return code;
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
