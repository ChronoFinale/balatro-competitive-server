package com.balatro.engine.net;

import com.balatro.engine.state.Ruleset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * A minimal playable client: embeds the server in-process, logs in, and gives a
 * terminal REPL to play a solo run. One command to start — `./gradlew play`.
 *
 * Commands: new [seed] | play 0 1 2 3 4 | discard 0 1 | buy 0 | reroll | proceed
 *           | help | quit
 */
public final class ClientCli {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
        String user = args.length > 0 ? args[0] : "player";

        GameServer server = new GameServer(Ruleset.standard()).start(0);
        int port = server.port();
        HttpClient http = HttpClient.newHttpClient();
        String token = login(http, port, user);

        WebSocket ws = http.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/game"), new Printer())
                .join();
        ws.sendText(JSON.writeValueAsString(Map.of("type", "auth", "seq", 0, "token", token)), true).join();
        Thread.sleep(150);

        printHelp();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        long seq = 1;
        System.out.print("> ");
        String line;
        while ((line = in.readLine()) != null) {
            String cmd = line.trim();
            if (cmd.equals("quit") || cmd.equals("exit")) break;
            if (cmd.isEmpty() || cmd.equals("help")) {
                printHelp();
                System.out.print("> ");
                continue;
            }
            Map<String, Object> msg = parseCommand(cmd);
            if (msg == null) {
                System.out.println("? unknown command (try 'help')");
                System.out.print("> ");
                continue;
            }
            msg.put("seq", seq++);
            ws.sendText(JSON.writeValueAsString(msg), true).join();
            Thread.sleep(150); // let the async response print before the next prompt
            System.out.print("> ");
        }
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        server.close();
        System.out.println("bye");
        System.exit(0);
    }

    /** Parse a REPL line into a protocol message, or null if unrecognized. */
    public static Map<String, Object> parseCommand(String line) {
        String[] t = line.trim().split("\\s+");
        Map<String, Object> m = new HashMap<>();
        switch (t[0]) {
            case "new" -> {
                m.put("type", "newRun");
                m.put("seed", t.length > 1 ? t[1] : "SEED");
            }
            case "play" -> {
                m.put("type", "playHand");
                m.put("cards", ints(t));
            }
            case "discard" -> {
                m.put("type", "discard");
                m.put("cards", ints(t));
            }
            case "buy" -> {
                m.put("type", "buyJoker");
                m.put("index", t.length > 1 ? Integer.parseInt(t[1]) : 0);
            }
            case "reroll" -> m.put("type", "reroll");
            case "proceed", "go" -> m.put("type", "proceed");
            default -> {
                return null;
            }
        }
        return m;
    }

    private static List<Integer> ints(String[] tokens) {
        List<Integer> r = new ArrayList<>();
        for (int i = 1; i < tokens.length; i++) {
            try {
                r.add(Integer.parseInt(tokens[i]));
            } catch (NumberFormatException ignored) {
            }
        }
        return r;
    }

    private static void printHelp() {
        System.out.println("""
                commands:
                  new [seed]        start a run
                  play 0 1 2 3 4    play those hand slots
                  discard 0 1       discard those hand slots
                  buy 0             buy shop joker at slot
                  reroll            reroll the shop
                  proceed           leave shop -> next blind
                  help | quit""");
    }

    // ---- async server output ----

    private static final class Printer implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                print(buf.toString());
                buf.setLength(0);
            }
            ws.request(1);
            return null;
        }
    }

    private static void print(String raw) {
        try {
            JsonNode n = JSON.readTree(raw);
            switch (n.path("type").asText()) {
                case "authed" -> System.out.println("\n  logged in as " + n.path("playerId").asText());
                case "error" -> System.out.println("\n  x " + n.path("rejection").asText());
                case "update" -> printUpdate(n);
                default -> System.out.println("\n  [" + n.path("type").asText() + "] " + raw);
            }
        } catch (Exception e) {
            System.out.println("\n  " + raw);
        }
    }

    private static void printUpdate(JsonNode n) {
        if (!n.path("accepted").asBoolean(true)) {
            System.out.println("\n  x " + n.path("rejection").asText());
            return;
        }
        JsonNode v = n.path("view");
        StringBuilder sb = new StringBuilder("\n  ");
        sb.append(v.path("phase").asText()).append(" | ante ").append(v.path("ante").asInt())
                .append(" ").append(v.path("blind").asText())
                .append(" | score ").append(v.path("roundScore").asLong())
                .append("/").append(v.path("requirement").asLong())
                .append(" | $").append(v.path("money").asInt());
        System.out.println(sb);

        if (v.path("jokers").size() > 0) {
            List<String> js = new ArrayList<>();
            v.path("jokers").forEach(j -> js.add(j.asText()));
            System.out.println("  jokers: " + String.join(", ", js));
        }

        StringBuilder hand = new StringBuilder("  hand: ");
        JsonNode cards = v.path("hand");
        for (int i = 0; i < cards.size(); i++) {
            JsonNode c = cards.get(i);
            hand.append(i).append(":").append(card(c)).append(" ");
        }
        System.out.println(hand);

        if (v.path("shop").isArray() && v.path("shop").size() > 0) {
            StringBuilder shop = new StringBuilder("  shop: ");
            JsonNode items = v.path("shop");
            for (int i = 0; i < items.size(); i++) {
                JsonNode it = items.get(i);
                shop.append(i).append(":").append(it.path("name").asText())
                        .append("($").append(it.path("cost").asInt()).append(") ");
            }
            shop.append(" (reroll $").append(v.path("rerollCost").asInt()).append(")");
            System.out.println(shop);
        }

        JsonNode replay = n.path("replay");
        if (replay.isArray() && replay.size() > 0) {
            JsonNode lastEntry = replay.get(replay.size() - 1);
            System.out.println("  scored: " + lastEntry.path("runningChips").asLong()
                    + " x " + lastEntry.path("runningMult").asDouble());
        }
    }

    private static String card(JsonNode c) {
        return rankShort(c.path("rank").asText()) + suitShort(c.path("suit").asText());
    }

    private static String rankShort(String rank) {
        return switch (rank) {
            case "TWO" -> "2"; case "THREE" -> "3"; case "FOUR" -> "4"; case "FIVE" -> "5";
            case "SIX" -> "6"; case "SEVEN" -> "7"; case "EIGHT" -> "8"; case "NINE" -> "9";
            case "TEN" -> "T"; case "JACK" -> "J"; case "QUEEN" -> "Q"; case "KING" -> "K";
            case "ACE" -> "A"; default -> "?";
        };
    }

    private static String suitShort(String suit) {
        return switch (suit) {
            case "SPADES" -> "s"; case "HEARTS" -> "h"; case "CLUBS" -> "c"; case "DIAMONDS" -> "d";
            default -> "?";
        };
    }

    private static String login(HttpClient http, int port, String username) throws Exception {
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/login"))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("username", username))))
                .build(), HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(resp.body()).path("token").asText();
    }
}
