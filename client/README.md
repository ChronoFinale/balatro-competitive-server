# Balatro Competitive — Electron reference client

A **thin** client: it renders the server's `ClientView` and sends intents over the
**raw-TCP** wire protocol (the same one the Balatro Lua mod will speak). All game logic
and scoring is server-authoritative — this client computes nothing that affects the game.

- **Main process** (`src/main`) owns the TCP socket to the Java server: newline-delimited
  JSON framing, a 15s heartbeat ping, and automatic reconnect (re-auths with the stored
  token, so the server resumes the run within its grace window). `setNoDelay` for low latency.
- **Preload** (`src/preload`) exposes a minimal, sandboxed IPC bridge (`window.balatro`).
- **Renderer** (`src/renderer`) is React + TanStack Store, rendering the authoritative view.

## Run it

1. Start the Java server (from the repo root) — it listens on **28788** (HTTP login / WebSocket)
   and **28789** (raw TCP, what this client uses):

   ```
   ./gradlew run            # or run com.balatromp.engine.net.ServerMain
   ```

2. Launch the client:

   ```
   cd client
   npm install
   npm run dev              # opens the Electron window, connects to 127.0.0.1:28789
   ```

Override the target with `BALATRO_HOST`, `BALATRO_HTTP_PORT`, `BALATRO_TCP_PORT` env vars.

## Build

```
npm run build              # bundles main + preload + renderer into out/
npm run typecheck          # tsc --noEmit
```

In production, TLS is terminated by a reverse proxy (Caddy) in front of the server; the
client connects to the proxy. The Lua-mod client speaks this same JSON-over-TCP protocol.
