import { contextBridge, ipcRenderer } from "electron";

/** The only surface the renderer can touch — no Node, no raw socket. */
const api = {
  login: (username: string): Promise<string> => ipcRenderer.invoke("balatro:login", username),
  connect: (token: string): Promise<boolean> => ipcRenderer.invoke("balatro:connect", token),
  send: (obj: unknown): void => ipcRenderer.send("balatro:send", obj),
  disconnect: (): void => ipcRenderer.send("balatro:disconnect"),
  onMessage: (cb: (m: any) => void): (() => void) => {
    const h = (_e: unknown, m: any) => cb(m);
    ipcRenderer.on("balatro:message", h);
    return () => ipcRenderer.removeListener("balatro:message", h);
  },
  onStatus: (cb: (s: { state: string; detail?: string }) => void): (() => void) => {
    const h = (_e: unknown, s: { state: string; detail?: string }) => cb(s);
    ipcRenderer.on("balatro:status", h);
    return () => ipcRenderer.removeListener("balatro:status", h);
  },
};

contextBridge.exposeInMainWorld("balatro", api);

export type BalatroApi = typeof api;
