export {};

declare global {
  interface Window {
    // The preload bridge (see src/preload/index.ts). The renderer's only IPC surface.
    balatro: {
      login(username: string): Promise<string>;
      connect(token: string): Promise<boolean>;
      send(obj: unknown): void;
      disconnect(): void;
      onMessage(cb: (m: any) => void): () => void;
      onStatus(cb: (s: { state: string; detail?: string }) => void): () => void;
    };
  }
}
