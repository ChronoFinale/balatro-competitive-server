package com.balatro.content;

import com.balatro.engine.ui.*;
import com.balatro.engine.ui.UiComponent.*;

import com.balatro.engine.ui.UiComponent.Button;
import com.balatro.engine.ui.UiComponent.Input;
import com.balatro.engine.ui.UiComponent.Label;
import com.balatro.engine.ui.UiComponent.Nav;
import com.balatro.engine.ui.UiComponent.Select;
import com.balatro.engine.ui.UiComponent.Stat;
import java.util.List;
import java.util.Map;

/**
 * The first-party screens, authored as data and compiled to {@code /content/ui-screens.json}. A generic
 * renderer interprets them, so the lobby / selection / queue UX is defined once and rendered by any client
 * (Electron now, Lua later). Adding a screen or a button is a line here, not a new hand-coded component.
 */
public final class Screens {

    private Screens() {}

    public static List<UiScreen> all() {
        return List.of(
                // Main menu: pick a ruleset the server offers; start a solo run or go to multiplayer.
                new UiScreen("menu", "Balatro Competitive", List.of(
                        new Label("Pick a ruleset and start a run, or play multiplayer."),
                        new Select("rs", "Ruleset", "rulesets", "ruleset"),
                        new Button("Solo Run", "newRun", Map.of("ruleset", "$ruleset")),
                        new Nav("Multiplayer ▸", "mp"))),

                // Multiplayer: create a lobby, join one by code, or quick-match via the queue.
                new UiScreen("mp", "Multiplayer", List.of(
                        new Button("Create Lobby", "createLobby", Map.of()),
                        new Stat("Lobby code", "lobbyCode"),
                        new Input("code", "Join by code", "code", "ABCD12"),
                        new Button("Join Lobby", "joinLobby", Map.of("code", "$code")),
                        new Nav("Quick Match ▸", "queue"),
                        new Nav("◂ Back", "menu"))),

                // Queue: kick off matchmaking, show status, allow leaving.
                new UiScreen("queue", "Finding a Match", List.of(
                        new Button("Join Queue", "joinQueue", Map.of("ruleset", "$ruleset")),
                        new Stat("Status", "queueStatus"),
                        new Button("Leave Queue", "leaveQueue", Map.of()),
                        new Nav("◂ Back", "mp"))));
    }
}
