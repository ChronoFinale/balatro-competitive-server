package com.balatromp.engine.net;

import com.balatromp.engine.state.Ruleset;

/** Runnable entry point: starts the authoritative WebSocket game server. */
public final class ServerMain {

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8788;
        GameServer server = new GameServer(Ruleset.standard()).start(port);
        System.out.println("Balatro competitive server (WebSocket) listening on "
                + "ws://127.0.0.1:" + server.port() + "/game");
    }
}
