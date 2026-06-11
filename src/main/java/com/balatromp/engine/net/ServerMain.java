package com.balatromp.engine.net;

import com.balatromp.engine.state.Ruleset;

/**
 * Runnable entry point: starts the authoritative game server on both transports —
 * raw TCP (the protocol the Balatro Lua mod + Electron reference client speak) and
 * WebSocket (browser tooling). Args: [httpPort] [tcpPort], defaults 8788 / 8789.
 */
public final class ServerMain {

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8788;
        int tcpPort = args.length > 1 ? Integer.parseInt(args[1]) : 8789;
        GameServer server = new GameServer(Ruleset.standard()).start(port).startTcp(tcpPort);
        System.out.println("Balatro competitive server listening on:");
        System.out.println("  raw TCP   tcp://127.0.0.1:" + server.tcpPort() + "  (game protocol)");
        System.out.println("  WebSocket ws://127.0.0.1:" + server.port() + "/game");
    }
}
