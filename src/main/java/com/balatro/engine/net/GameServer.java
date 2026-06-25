package com.balatro.engine.net;

import com.balatro.engine.auth.Account;
import com.balatro.engine.auth.AccountStore;
import com.balatro.engine.auth.Identity;
import com.balatro.engine.auth.OAuthProvider;
import com.balatro.engine.auth.ProviderRegistry;
import com.balatro.engine.game.Match;
import com.balatro.engine.game.Run;
import com.balatro.engine.intent.Intent;
import com.balatro.engine.intent.RunAction;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.def.BuilderSchema;
import com.balatro.engine.joker.def.CustomJokerStore;
import com.balatro.grammar.JokerDef;
import com.balatro.engine.state.Ruleset;
import com.balatro.engine.state.RulesetStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Transport adapter wrapped around the authoritative engine. No game logic lives
 * here — it authenticates a connection, parses an {@link Intent}, runs it through
 * that player's {@link Run}, and serializes the {@link ServerUpdate}.
 *
 * <p>The intent-routing core is transport-neutral ({@link #handle}, keyed on a
 * {@link Connection}); two adapters feed it the same JSON protocol:
 * <ul>
 *   <li><b>Raw TCP</b> (newline-delimited JSON) — what the Balatro Lua mod and the
 *       Electron reference client speak. Started via {@link #startTcp(int)}.
 *   <li><b>WebSocket</b> (Javalin/Jetty) — for browser clients/tooling.
 * </ul>
 *
 * <p>Flow: HTTP POST /login -> token. A connection's first message must be
 * {@code {"type":"auth","token":...}}; only then are game intents accepted. There
 * is no message type that carries a client-supplied score.
 */
public final class GameServer implements AutoCloseable {

    /** The baseline mod/client version this server is built for. A thin client compares its own version to
     *  this on bootstrap; if older, it prompts a restart-to-update (mod CODE needs a Lovely reboot, unlike
     *  content/config which sync live). Bump when the shipped mod shell changes. */
    public static final String MOD_VERSION = "0.1.0";

    private final Javalin app;
    private final Ruleset ruleset;
    private final CustomJokerStore jokerStore;
    private final RulesetStore rulesetStore;
    private final AuthService auth = new AuthService();
    private final AccountStore accounts = new AccountStore(
            new java.io.File("web-assets/accounts").getAbsoluteFile().toPath());
    private final ProviderRegistry providers = new ProviderRegistry();
    private final ObjectMapper json = new ObjectMapper();

    private final Map<String, String> players = new ConcurrentHashMap<>();      // sessionId -> playerId
    private final Map<String, Connection> conns = new ConcurrentHashMap<>();    // sessionId -> connection
    private final Map<String, Connection> connByPlayer = new ConcurrentHashMap<>(); // playerId -> current connection
    private final Map<String, Run> runs = new ConcurrentHashMap<>();            // playerId -> solo run (survives disconnect)
    private final Map<String, ScheduledFuture<?>> graceTasks = new ConcurrentHashMap<>(); // playerId -> pending forfeit
    private final Map<String, Match> matchBySession = new ConcurrentHashMap<>();
    private final Map<String, Match> pendingByCode = new ConcurrentHashMap<>();
    private final Set<String> activeCodes = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "session-grace");
                t.setDaemon(true);
                return t;
            });

    /** How long a disconnected player's game is preserved before cleanup (crash/reconnect window). */
    private volatile int graceSeconds = 180;

    private volatile ServerSocket tcpServer; // raw-TCP listener (null until startTcp)

    public GameServer(Ruleset ruleset) {
        this(ruleset,
                new CustomJokerStore(new java.io.File("web-assets/custom-jokers").getAbsoluteFile().toPath()),
                new RulesetStore(new java.io.File("web-assets/custom-rulesets").getAbsoluteFile().toPath()));
    }

    public GameServer(Ruleset ruleset, CustomJokerStore jokerStore) {
        this(ruleset, jokerStore,
                new RulesetStore(new java.io.File("web-assets/custom-rulesets").getAbsoluteFile().toPath()));
    }

    public GameServer(Ruleset ruleset, CustomJokerStore jokerStore, RulesetStore rulesetStore) {
        this.ruleset = ruleset;
        this.jokerStore = jokerStore;
        this.rulesetStore = rulesetStore;
        jokerStore.loadAll();   // register custom jokers first...
        rulesetStore.loadAll(); // ...so custom rulesets can reference them
        // Custom composable bundles (a new mode authored as JSON, no code) — joins the selectable rulesets.
        com.balatro.engine.state.BundleCatalog.loadDir(
                new java.io.File("web-assets/custom-bundles").getAbsoluteFile().toPath());
        accounts.loadAll();     // persisted player accounts
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

            registerBuilderRoutes(cfg);    // joker builder: schema, list, save, sprite, custom assets
            registerRulesetRoutes(cfg);    // custom rulesets + composable bundles
            registerContentSyncRoutes(cfg); // delta-sync manifest/files + bootstrap
            registerAuthRoutes(cfg);       // OAuth + dev login

            // Game socket (WebSocket transport).
            cfg.routes.ws("/game", ws -> {
                ws.onMessage(ctx -> handle(new WsConnection(ctx), ctx.message()));
                ws.onClose(ctx -> dropSession(ctx.sessionId()));
            });
        });
    }

    /** Joker builder: the building-block vocabulary the UI renders, the custom-joker list + the pool of
     *  every selectable joker, create/replace a joker from JSON, sprite upload, and serving custom assets. */
    private void registerBuilderRoutes(io.javalin.config.JavalinConfig cfg) {
        // The building-block vocabulary the builder UI renders (driven by real enums).
        cfg.routes.get("/jokers/schema", ctx -> ctx.json(BuilderSchema.build()));
        // List every custom joker authored so far.
        cfg.routes.get("/jokers", ctx -> ctx.json(jokerStore.all()));
        // Every joker selectable for a ruleset pool (curated built-ins + custom).
        cfg.routes.get("/jokers/available", ctx -> ctx.json(availableJokers()));
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
    }

    /** Custom rulesets + composable bundles: list the rulesets a lobby can use, save a custom ruleset, and
     *  list/register composable bundles (a new mode authored as JSON, validated by resolving its content). */
    private void registerRulesetRoutes(io.javalin.config.JavalinConfig cfg) {
        // Rulesets the lobby/builder can use (curated + custom).
        cfg.routes.get("/rulesets", ctx -> ctx.json(rulesetStore.all()));
        // Create a custom ruleset (a bundle that, with its joker pool, dictates a match).
        cfg.routes.post("/rulesets", ctx -> {
            try {
                Ruleset r = json.readValue(ctx.body(), Ruleset.class);
                ctx.json(rulesetStore.save(r));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", String.valueOf(e.getMessage())));
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "invalid ruleset: " + e.getMessage()));
            }
        });
        // Composable bundles (content overlays + capabilities + mode). GET lists; POST registers a custom
        // mode at runtime (validated by resolving its content), joining the selectable rulesets.
        cfg.routes.get("/bundles", ctx -> ctx.json(com.balatro.engine.state.BundleCatalog.names()));
        cfg.routes.post("/bundles", ctx -> {
            try {
                var b = json.readValue(ctx.body(), com.balatro.engine.state.RulesetBundle.class);
                b.content(); // validate it resolves (known base + every overlay exists)
                com.balatro.engine.state.BundleCatalog.register(b);
                ctx.json(Map.of("name", b.name(), "registered", true));
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "invalid bundle: " + e.getMessage()));
            }
        });
    }

    /** Content auto-update: the delta-sync manifest (version + per-file sha256), the raw hash-exact bytes of
     *  the files it lists, and /bootstrap (the single "what should this client do now" call a thin client
     *  makes at launch — content version to sync, offered modes, feature flags, mod-code freshness). */
    private void registerContentSyncRoutes(io.javalin.config.JavalinConfig cfg) {
        cfg.routes.get("/content/manifest", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*"); // public content; readable by any client
            ctx.contentType("application/json");
            ctx.result(resourceBytes("/content/manifest.json"));
        });
        cfg.routes.get("/content/file", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            String p = ctx.queryParam("path");
            if (p == null || !com.balatro.engine.codegen.ContentManifest.FILES.contains(p)) {
                ctx.status(404).result("unknown content file");
                return;
            }
            ctx.contentType("application/json");
            ctx.result(resourceBytes("/" + p));   // path validated against the manifest list (no traversal)
        });
        cfg.routes.get("/bootstrap", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            String contentVersion = "";
            try {
                contentVersion = json.readTree(resourceBytes("/content/manifest.json")).path("version").asText("");
            } catch (Exception ignored) { /* no manifest -> empty version */ }
            ctx.json(Map.of(
                    "modVersion", MOD_VERSION,           // the mod compares its own version; older -> restart
                    "contentVersion", contentVersion,    // sync /content if this differs from the cached one
                    "rulesets", rulesetStore.names(),    // the modes/rulesets to offer in the lobby
                    "features", Map.of("lobby", true, "queue", true)));
        });
    }

    /** OAuth account linkage: list providers, begin/finish a provider login, validate a stored token
     *  (reuse on app load), and the dev username login. Pulled out of the create() block for legibility. */
    private void registerAuthRoutes(io.javalin.config.JavalinConfig cfg) {
        // Which providers are available (mock always; Google/Discord when configured).
        cfg.routes.get("/auth/providers", ctx -> ctx.json(providers.names()));

        // Begin login: returns the provider authorize URL (+ state) to send the user to.
        cfg.routes.get("/auth/{provider}/start", ctx -> {
            OAuthProvider p = providers.get(ctx.pathParam("provider")).orElse(null);
            if (p == null) {
                ctx.status(404).json(Map.of("error", "unknown provider"));
                return;
            }
            String redirect = ctx.queryParam("redirect");
            if (redirect == null) redirect = "/auth/" + p.name() + "/callback";
            String state = java.util.UUID.randomUUID().toString();
            ctx.json(Map.of("authorizeUrl", p.authorizeUrl(state, redirect), "state", state));
        });

        // Finish login: exchange the code -> identity -> account -> reusable session token.
        cfg.routes.get("/auth/{provider}/callback", ctx -> {
            OAuthProvider p = providers.get(ctx.pathParam("provider")).orElse(null);
            if (p == null) {
                ctx.status(404).json(Map.of("error", "unknown provider"));
                return;
            }
            String redirect = ctx.queryParam("redirect");
            if (redirect == null) redirect = "/auth/" + p.name() + "/callback";
            try {
                Identity id = p.exchange(ctx.queryParam("code"), redirect);
                Account acc = accounts.upsert(p.name(), id);
                String token = auth.issueForAccount(acc.id(), acc.displayName());
                ctx.json(Map.of("token", token, "playerId", acc.id(),
                        "accountId", acc.id(), "name", acc.displayName()));
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "login failed: " + e.getMessage()));
            }
        });

        // Validate a stored token and return the account (token reuse on app load).
        cfg.routes.get("/auth/me", ctx -> {
            String token = ctx.queryParam("token");
            String id = token != null ? auth.verify(token) : null;
            if (id == null) {
                ctx.status(401).json(Map.of("error", "invalid or expired token"));
                return;
            }
            Account acc = accounts.get(id);
            ctx.json(Map.of("accountId", id, "playerId", id,
                    "name", acc != null ? acc.displayName() : auth.displayName(token)));
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
    }

    public GameServer start(int port) {
        app.start("127.0.0.1", port);
        return this;
    }

    public int port() {
        return app.port();
    }

    /**
     * Start the raw-TCP transport (newline-delimited JSON) on {@code tcpPort}
     * (0 = ephemeral). This is the wire protocol the Balatro Lua mod and the
     * Electron reference client speak. Returns {@code this}.
     */
    public GameServer startTcp(int tcpPort) {
        try {
            ServerSocket ss = new ServerSocket();
            ss.bind(new InetSocketAddress("127.0.0.1", tcpPort));
            this.tcpServer = ss;
        } catch (IOException e) {
            throw new RuntimeException("TCP bind failed: " + e.getMessage(), e);
        }
        Thread accept = new Thread(this::acceptLoop, "tcp-accept");
        accept.setDaemon(true);
        accept.start();
        return this;
    }

    /** The bound raw-TCP port (-1 if not started). */
    public int tcpPort() {
        return tcpServer != null ? tcpServer.getLocalPort() : -1;
    }

    @Override
    public void close() {
        app.stop();
        scheduler.shutdownNow();
        ServerSocket ss = tcpServer;
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException ignored) {
                // shutting down
            }
        }
    }

    // ---- transport-neutral core --------------------------------------------

    /** A client connection, independent of transport: how the core replies to one client. */
    public interface Connection {
        String sessionId();

        void send(Object payload);
    }

    /** WebSocket-backed connection. */
    private final class WsConnection implements Connection {
        private final WsContext ctx;

        WsConnection(WsContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public String sessionId() {
            return ctx.sessionId();
        }

        @Override
        public void send(Object payload) {
            try {
                ctx.send(json.writeValueAsString(payload));
            } catch (Exception ignored) {
                // connection went away; authoritative state is untouched
            }
        }
    }

    /** Raw-TCP-backed connection (one newline-delimited JSON message per line). */
    private final class TcpConnection implements Connection {
        private final String sessionId = UUID.randomUUID().toString();
        private final Writer out;

        TcpConnection(OutputStream os) {
            this.out = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        }

        @Override
        public String sessionId() {
            return sessionId;
        }

        @Override
        public synchronized void send(Object payload) {
            try {
                out.write(json.writeValueAsString(payload));
                out.write('\n');
                out.flush();
            } catch (Exception ignored) {
                // connection went away; authoritative state is untouched
            }
        }
    }

    private void acceptLoop() {
        ServerSocket ss = tcpServer;
        while (ss != null && !ss.isClosed()) {
            try {
                Socket socket = ss.accept();
                // One virtual thread per connection (Java 21+): cheap to have thousands
                // blocked on socket reads, so the simple blocking read loop scales.
                Thread.ofVirtual().name("tcp-conn").start(() -> serveTcp(socket));
            } catch (IOException e) {
                return; // server closed
            }
        }
    }

    private void serveTcp(Socket socket) {
        TcpConnection conn;
        try {
            socket.setTcpNoDelay(true);          // send small intents immediately (no Nagle delay)
            socket.setSoTimeout(45_000);          // ~3 missed 15s heartbeats -> treat as dead
            conn = new TcpConnection(socket.getOutputStream());
        } catch (IOException e) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) handle(conn, line);
            }
        } catch (IOException ignored) {
            // client dropped
        } finally {
            dropSession(conn.sessionId());
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing
            }
        }
    }

    /**
     * A connection went away. The game state is owned by the player identity, not the
     * socket, so we DON'T destroy it here — we detach the connection and start a grace
     * timer; if the player re-auths within {@link #graceSeconds} the run is resumed,
     * otherwise it's cleaned up. (Match reconnect is a follow-up; matches still detach.)
     */
    private void dropSession(String sessionId) {
        String playerId = players.remove(sessionId);
        conns.remove(sessionId);
        matchBySession.remove(sessionId);
        if (playerId == null) return;
        Connection current = connByPlayer.get(playerId);
        if (current != null && current.sessionId().equals(sessionId)) {
            connByPlayer.remove(playerId); // this was their live connection
            scheduleGrace(playerId);
        }
    }

    /** Preserve a disconnected player's run for the grace window, then drop it if still gone. */
    private void scheduleGrace(String playerId) {
        ScheduledFuture<?> prev = graceTasks.remove(playerId);
        if (prev != null) prev.cancel(false);
        ScheduledFuture<?> f = scheduler.schedule(() -> {
            graceTasks.remove(playerId);
            if (!connByPlayer.containsKey(playerId)) runs.remove(playerId); // never came back
        }, graceSeconds, TimeUnit.SECONDS);
        graceTasks.put(playerId, f);
    }

    /** Test/config hook: shorten the reconnect grace window. */
    public void setGraceSeconds(int seconds) {
        this.graceSeconds = seconds;
    }

    /** Dispatch one client message (same logic for every transport). */
    private void handle(Connection conn, String message) {
        long seq = 0; // hoisted so a parse failure still correlates its error reply to the request
        try {
            JsonNode msg = json.readTree(message);
            String type = msg.path("type").asText();
            seq = msg.path("seq").asLong();

            if (type.equals("auth")) {
                String playerId = auth.verify(msg.path("token").asText());
                if (playerId == null) {
                    conn.send(error(seq, "invalid token"));
                    return;
                }
                players.put(conn.sessionId(), playerId);
                conns.put(conn.sessionId(), conn);
                connByPlayer.put(playerId, conn);
                // Reconnected within the grace window: cancel the pending cleanup.
                ScheduledFuture<?> grace = graceTasks.remove(playerId);
                if (grace != null) grace.cancel(false);
                conn.send(Map.of("type", "authed", "seq", seq, "playerId", playerId,
                        "rulesets", rulesetStore.names()));
                // Resume: if a solo run is still alive for this identity, resend its view so the
                // reconnected client re-renders the exact authoritative state.
                Run existing = runs.get(playerId);
                if (existing != null) {
                    conn.send(new WsResponse("update", seq, true, null, existing.view(), List.of()));
                }
                return;
            }

            if (type.equals("ping")) { // heartbeat: keep-alive + liveness probe
                conn.send(Map.of("type", "pong", "seq", seq));
                return;
            }

            if (!players.containsKey(conn.sessionId())) {
                conn.send(error(seq, "unauthenticated"));
                return;
            }

            switch (type) {
                case "newRun" -> handleNewRun(conn, seq, msg);
                case "setLocale" -> {
                    Run r = runs.get(players.get(conn.sessionId()));
                    if (r != null) {
                        r.viewLocale = msg.path("locale").asText("en");
                        conn.send(ok(seq, r)); // re-render the view in the new locale
                    }
                }
                case "joinQueue" -> joinQueue(conn, seq);
                case "leaveQueue" -> leaveQueue(conn, seq);
                case "createLobby" -> createLobby(conn, seq);
                case "joinLobby" -> joinLobby(conn, seq, msg.path("code").asText());
                case "proposeRuleset" -> {
                    Match m = matchBySession.get(conn.sessionId());
                    if (m != null) m.propose(conn.sessionId(), msg.path("name").asText());
                    else conn.send(error(seq, "not in a match"));
                }
                case "respondRuleset" -> {
                    Match m = matchBySession.get(conn.sessionId());
                    if (m != null) m.respond(conn.sessionId(), msg.path("accept").asBoolean());
                    else conn.send(error(seq, "not in a match"));
                }
                case "preview" -> {
                    Run run = runFor(conn.sessionId());
                    if (run == null) {
                        conn.send(error(seq, "no active run"));
                        break;
                    }
                    var pre = run.previewScore(ints(msg.path("cards"))); // read-only, no state change
                    conn.send(Map.of("type", "preview", "seq", seq,
                            "chips", pre != null ? pre.chips() : 0L,
                            "mult", pre != null ? pre.mult() : 0.0,
                            "score", pre != null ? pre.score() : 0.0,
                            "replay", pre != null ? pre.replayLog() : List.of()));
                }
                // Every run-mutating message becomes a RunAction applied via the single fold entry point
                // (Run.apply), which records it to the run's action log. buyJoker/buyPlanet/buyConsumable
                // and openBooster/skipBooster are kept as aliases for older clients.
                case "playHand" -> applyAction(conn, seq, new RunAction.PlayHand(ints(msg.path("cards"))));
                case "discard" -> applyAction(conn, seq, new RunAction.Discard(ints(msg.path("cards"))));
                case "buyShopItem", "buyJoker", "buyPlanet", "buyConsumable" ->
                        applyAction(conn, seq, new RunAction.BuyShopItem(index(msg)));
                case "reroll" -> applyAction(conn, seq, new RunAction.Reroll());
                case "selectBlind" -> applyAction(conn, seq, new RunAction.SelectBlind());
                case "skipBlind" -> applyAction(conn, seq, new RunAction.SkipBlind());
                case "buyVoucher" -> applyAction(conn, seq, new RunAction.BuyVoucher(index(msg)));
                case "openPack", "openBooster" -> applyAction(conn, seq, new RunAction.OpenPack(index(msg)));
                case "pickPackItem" -> applyAction(conn, seq, new RunAction.PickPackItem(index(msg)));
                case "skipPack", "skipBooster" -> applyAction(conn, seq, new RunAction.SkipPack());
                case "sellJoker" -> applyAction(conn, seq, new RunAction.SellJoker(index(msg)));
                case "useConsumable" -> applyAction(conn, seq,
                        new RunAction.UseConsumable(index(msg), uuids(msg.path("targets"))));
                case "proceed" -> applyAction(conn, seq, new RunAction.Proceed());
                default -> conn.send(error(seq, "unknown type: " + type));
            }
        } catch (Exception e) {
            conn.send(error(seq, "bad message: " + e.getMessage()));
        }
    }

    /** Every joker selectable for a ruleset pool: curated built-ins + authored custom. */
    /** Raw bytes of a classpath resource (for hash-exact content downloads); empty if absent. */
    private static byte[] resourceBytes(String path) {
        try (var in = GameServer.class.getResourceAsStream(path)) {
            return in == null ? new byte[0] : in.readAllBytes();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("reading " + path, e);
        }
    }

    private List<Map<String, Object>> availableJokers() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : JokerLibrary.builtinKeys()) {
            var info = JokerLibrary.create(key).info();
            out.add(jokerCard(info.key(), info.name(), info.rarity(), info.cost(), false));
        }
        for (JokerDef def : jokerStore.all()) {
            out.add(jokerCard(def.key(), def.name(), def.rarity(), def.cost(), true));
        }
        return out;
    }

    private static Map<String, Object> jokerCard(String key, String name, String rarity, int cost, boolean custom) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("key", key);
        m.put("name", name);
        m.put("rarity", rarity);
        m.put("cost", cost);
        m.put("custom", custom);
        return m;
    }

    /** Start a solo run. The client sends only NAMES (ruleset/deck/stake) — never content, never a chosen
     *  seed (anti-cheat): the server resolves each, defaults sensibly, and generates the seed itself (a
     *  supplied seed is honored only for dev/reproducible wire tests). The run is keyed by identity so it
     *  survives reconnect. */
    private void handleNewRun(Connection conn, long seq, JsonNode msg) {
        String rsName = msg.path("ruleset").asText("");
        Ruleset rs = rsName.isEmpty() ? ruleset : rulesetStore.get(rsName);
        if (rs == null) rs = ruleset;
        com.balatro.engine.state.Stake stake =
                com.balatro.engine.state.Stake.parse(msg.path("stake").asText(null));
        com.balatro.engine.game.DeckCatalog.DeckType deck =
                com.balatro.engine.game.DeckCatalog.get(msg.path("deck").asText(rs.deckType()));
        String seedArg = msg.path("seed").asText("");
        String seed = seedArg.isEmpty() ? com.balatro.engine.rng.Seeds.random() : seedArg;
        Run run = new Run(rs, seed, stake, deck);
        run.viewLocale = msg.path("locale").asText("en"); // server renders ClientView text in this locale
        runs.put(players.get(conn.sessionId()), run); // keyed by identity, survives reconnect
        conn.send(ok(seq, run));
    }

    /** The player's authoritative Run: their match's run if in a match, else their solo run. */
    private Run runFor(String sessionId) {
        Match m = matchBySession.get(sessionId);
        return (m != null) ? m.runOf(sessionId) : runs.get(players.get(sessionId));
    }

    /** After applying an action, let the match push opponent state + decide the match. */
    private void afterAction(String sessionId) {
        Match m = matchBySession.get(sessionId);
        if (m != null) m.onAction(sessionId);
    }

    /**
     * The single entry point for every run-mutating message: fold the action into the player's Run
     * (match or solo) via {@link Run#apply}, which records it to the run's append-only action log, then
     * reply with the authoritative update and let the match react.
     */
    private void applyAction(Connection conn, long seq, RunAction action) {
        Run run = runFor(conn.sessionId());
        if (run == null) {
            conn.send(error(seq, "no active run"));
            return;
        }
        ServerUpdate up = run.apply(action);
        conn.send(WsResponse.of(seq, up));
        afterAction(conn.sessionId());
    }

    /** The one session waiting in the matchmaking queue (minimal 1-slot FIFO); null when empty. */
    private volatile String queuedSession;

    /** Join the match queue: pair with a waiting player into a Match (reusing the lobby flow), else wait. */
    private synchronized void joinQueue(Connection conn, long seq) {
        String me = conn.sessionId();
        if (queuedSession != null && !queuedSession.equals(me) && conns.containsKey(queuedSession)) {
            String host = queuedSession;
            queuedSession = null;
            Match match = new Match(newCode(), com.balatro.engine.rng.Seeds.random(), ruleset, rulesetStore, this::deliver);
            match.setHost(host, players.get(host));
            matchBySession.put(host, match);
            matchBySession.put(me, match);
            match.setGuestAndStart(me, players.get(me)); // both players matched -> ruleset agreement -> play
        } else {
            queuedSession = me;
            conn.send(Map.of("type", "queued", "seq", seq, "status", "waiting for an opponent"));
        }
    }

    private synchronized void leaveQueue(Connection conn, long seq) {
        if (conn.sessionId().equals(queuedSession)) queuedSession = null;
        conn.send(Map.of("type", "leftQueue", "seq", seq));
    }

    private void createLobby(Connection conn, long seq) {
        String code = newCode();
        String seed = com.balatro.engine.rng.Seeds.random(); // a valid, reproducible Balatro seed
        Match match = new Match(code, seed, ruleset, rulesetStore, this::deliver);
        match.setHost(conn.sessionId(), players.get(conn.sessionId()));
        pendingByCode.put(code, match);
        matchBySession.put(conn.sessionId(), match);
        conn.send(Map.of("type", "lobbyCreated", "seq", seq, "code", code));
    }

    private void joinLobby(Connection conn, long seq, String code) {
        Match match = pendingByCode.remove(code);
        if (match == null) {
            conn.send(error(seq, "no such lobby: " + code));
            return;
        }
        matchBySession.put(conn.sessionId(), match);
        match.setGuestAndStart(conn.sessionId(), players.get(conn.sessionId())); // pushes matchStart to both
    }

    /** Transport sink the Match uses to push to either player (incl. the opponent). */
    private void deliver(String sessionId, Object payload) {
        Connection c = conns.get(sessionId);
        if (c != null) c.send(payload);
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

    /** The optional {@code index} field of a message (0 when absent — the natural default for slot 0). */
    private static int index(JsonNode msg) {
        return msg.path("index").asInt();
    }

    private static List<Integer> ints(JsonNode arr) {
        List<Integer> r = new ArrayList<>();
        if (arr.isArray()) arr.forEach(n -> r.add(n.asInt()));
        return r;
    }

    /** Parse a JSON array of card-uid strings into UUIDs (consumable targets); skips any unparseable. */
    private static List<java.util.UUID> uuids(JsonNode arr) {
        List<java.util.UUID> r = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                try {
                    r.add(java.util.UUID.fromString(n.asText()));
                } catch (IllegalArgumentException ignored) {
                    // a malformed uid simply matches no card
                }
            }
        }
        return r;
    }

    /** The server -> client message envelope. */
    public record WsResponse(
            String type,
            long seq,
            boolean accepted,
            String rejection,
            ClientView view,
            List<com.balatro.engine.scoring.ReplayEntry> replay) {

        /** Wrap an authoritative {@link ServerUpdate} as an {@code "update"} reply (single transcription point). */
        static WsResponse of(long seq, ServerUpdate up) {
            return new WsResponse("update", seq, up.accepted(), up.rejection(), up.view(), up.replay());
        }
    }
}
