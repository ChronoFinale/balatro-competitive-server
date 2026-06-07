package com.balatromp.engine.net;

import com.balatromp.engine.state.Ruleset;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Load harness: starts the server in-process and drives N concurrent WebSocket
 * clients (one virtual thread each), each authenticating and playing a series of
 * hands, measuring per-message round-trip latency. Reports p50/p95/p99 latency
 * and throughput.
 *
 * Run: ./gradlew loadTest -Pargs="200 10"   (connections, hands-per-connection)
 *
 * NOTE: this runs over loopback, so it measures *server processing capacity*
 * (JSON + framing + scoring + dispatch), not user-perceived latency — that's
 * dominated by real network RTT and addressed by regional deployment.
 */
public final class LoadClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        int conns = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int hands = args.length > 1 ? Integer.parseInt(args[1]) : 10;

        GameServer server = new GameServer(Ruleset.standard()).start(0);
        int port = server.port();
        HttpClient http = HttpClient.newHttpClient();

        Queue<Long> latenciesNs = new ConcurrentLinkedQueue<>();
        LongAdder errors = new LongAdder();
        CountDownLatch done = new CountDownLatch(conns);

        System.out.printf("Load test: %d connections x %d hands -> %d messages%n",
                conns, hands, conns * hands);
        long start = System.nanoTime();

        for (int i = 0; i < conns; i++) {
            final int id = i;
            Thread.ofVirtual().start(() -> {
                try {
                    String token = login(http, port, "p" + id);
                    BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
                    WebSocket ws = connect(http, port, inbox);

                    ws.sendText(msg(Map.of("type", "auth", "seq", 0, "token", token)), true).join();
                    inbox.poll(5, TimeUnit.SECONDS);
                    ws.sendText(msg(Map.of("type", "newRun", "seq", 1, "seed", "L" + id)), true).join();
                    inbox.poll(5, TimeUnit.SECONDS);

                    for (int h = 0; h < hands; h++) {
                        long t0 = System.nanoTime();
                        ws.sendText(msg(Map.of("type", "playHand", "seq", h + 2,
                                "cards", List.of(0, 1, 2, 3, 4))), true).join();
                        String r = inbox.poll(5, TimeUnit.SECONDS);
                        if (r == null) { errors.increment(); break; }
                        latenciesNs.add(System.nanoTime() - t0);
                    }
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                } catch (Exception e) {
                    errors.increment();
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(120, TimeUnit.SECONDS);
        long elapsedNs = System.nanoTime() - start;
        server.close();
        report(latenciesNs, errors.sum(), elapsedNs);
    }

    private static void report(Queue<Long> latenciesNs, long errors, long elapsedNs) {
        List<Long> l = new ArrayList<>(latenciesNs);
        Collections.sort(l);
        double secs = elapsedNs / 1e9;
        System.out.println("----------------------------------------");
        System.out.printf("messages ok : %d%n", l.size());
        System.out.printf("errors      : %d%n", errors);
        System.out.printf("wall time   : %.2f s%n", secs);
        System.out.printf("throughput  : %.0f msg/s%n", l.isEmpty() ? 0 : l.size() / secs);
        if (!l.isEmpty()) {
            System.out.printf("latency p50 : %.2f ms%n", pct(l, 50) / 1e6);
            System.out.printf("latency p95 : %.2f ms%n", pct(l, 95) / 1e6);
            System.out.printf("latency p99 : %.2f ms%n", pct(l, 99) / 1e6);
            System.out.printf("latency max : %.2f ms%n", l.get(l.size() - 1) / 1e6);
        }
        System.out.println("----------------------------------------");
    }

    private static long pct(List<Long> sorted, int p) {
        int idx = Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1);
        return sorted.get(Math.max(0, idx));
    }

    private static String msg(Map<String, Object> m) throws Exception {
        return JSON.writeValueAsString(m);
    }

    private static String login(HttpClient http, int port, String username) throws Exception {
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/login"))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"" + username + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(resp.body()).path("token").asText();
    }

    private static WebSocket connect(HttpClient http, int port, BlockingQueue<String> inbox) {
        return http.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/game"), new WebSocket.Listener() {
                    private final StringBuilder buf = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buf.append(data);
                        if (last) {
                            inbox.offer(buf.toString());
                            buf.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).join();
    }
}
