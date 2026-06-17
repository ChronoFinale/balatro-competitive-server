import { app, shell, BrowserWindow, ipcMain } from "electron";
import { join } from "path";
import net from "net";

// The Java server (single source of truth). HTTP for /login, raw TCP for gameplay.
const HOST = process.env.BALATRO_HOST ?? "127.0.0.1";
const HTTP_PORT = Number(process.env.BALATRO_HTTP_PORT ?? 28788);
const TCP_PORT = Number(process.env.BALATRO_TCP_PORT ?? 28789);
const HEARTBEAT_MS = 15_000;

let win: BrowserWindow | null = null;

// ---- TCP transport (owned here; the renderer only sees parsed messages over IPC) ----

let socket: net.Socket | null = null;
let token: string | null = null;
let buffer = ""; // accumulates partial lines (TCP is a byte stream)
let heartbeat: ReturnType<typeof setInterval> | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let intentionalClose = false;

function toRenderer(channel: string, payload: unknown) {
  win?.webContents.send(channel, payload);
}

function status(state: string, detail?: string) {
  toRenderer("balatro:status", { state, detail });
}

function writeLine(obj: unknown) {
  if (socket && !socket.destroyed) socket.write(JSON.stringify(obj) + "\n");
}

function connectTcp() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  intentionalClose = false;
  status("connecting");
  const s = new net.Socket();
  socket = s;
  s.setNoDelay(true); // flush small intents immediately (no Nagle delay)
  s.connect(TCP_PORT, HOST, () => {
    buffer = "";
    if (token) writeLine({ type: "auth", token, seq: 0 }); // re-auth -> server resumes the run
    status("connected");
    if (heartbeat) clearInterval(heartbeat);
    heartbeat = setInterval(() => writeLine({ type: "ping", seq: 0 }), HEARTBEAT_MS);
  });
  s.setEncoding("utf8");
  s.on("data", (chunk: string) => {
    buffer += chunk;
    let nl: number;
    while ((nl = buffer.indexOf("\n")) >= 0) {
      const line = buffer.slice(0, nl);
      buffer = buffer.slice(nl + 1);
      if (!line.trim()) continue;
      try {
        toRenderer("balatro:message", JSON.parse(line));
      } catch {
        // ignore malformed line
      }
    }
  });
  const onGone = () => {
    if (heartbeat) {
      clearInterval(heartbeat);
      heartbeat = null;
    }
    socket = null;
    if (intentionalClose) return;
    status("reconnecting");
    // back off briefly, then retry; on reconnect we re-auth and the server resumes.
    reconnectTimer = setTimeout(connectTcp, 1500);
  };
  s.on("close", onGone);
  s.on("error", () => status("error"));
}

async function login(username: string): Promise<string> {
  const res = await fetch(`http://${HOST}:${HTTP_PORT}/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ username }),
  });
  const body = (await res.json()) as { token: string };
  token = body.token;
  return body.token;
}

function registerIpc() {
  ipcMain.handle("balatro:login", (_e, username: string) => login(username));
  ipcMain.handle("balatro:connect", (_e, t: string) => {
    token = t;
    connectTcp();
    return true;
  });
  ipcMain.on("balatro:send", (_e, obj: unknown) => writeLine(obj));
  ipcMain.on("balatro:disconnect", () => {
    intentionalClose = true;
    socket?.destroy();
  });
}

function createWindow() {
  win = new BrowserWindow({
    width: 1100,
    height: 820,
    show: false,
    autoHideMenuBar: true,
    backgroundColor: "#155233",
    webPreferences: {
      preload: join(__dirname, "../preload/index.js"),
      sandbox: false,
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  win.on("ready-to-show", () => win?.show());
  win.webContents.setWindowOpenHandler((d) => {
    shell.openExternal(d.url);
    return { action: "deny" };
  });
  if (process.env["ELECTRON_RENDERER_URL"]) {
    win.loadURL(process.env["ELECTRON_RENDERER_URL"]);
  } else {
    win.loadFile(join(__dirname, "../renderer/index.html"));
  }
}

app.whenReady().then(() => {
  registerIpc();
  createWindow();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
