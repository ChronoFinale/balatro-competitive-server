package com.balatro.engine.ui;

import com.balatro.engine.ui.UiComponent.Button;
import com.balatro.engine.ui.UiComponent.Label;
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
                // Main menu: pick a ruleset the server offers, start a run (or join the queue).
                new UiScreen("menu", "New Run", List.of(
                        new Label("Pick a ruleset and start, or queue for a match."),
                        new Select("rs", "Ruleset", "rulesets", "ruleset"),
                        new Button("Solo Run", "newRun", Map.of("ruleset", "$ruleset")),
                        new Button("Find Match", "joinQueue", Map.of("ruleset", "$ruleset")))),

                // Queue: while matchmaking, show status; allow leaving.
                new UiScreen("queue", "Finding a Match", List.of(
                        new Stat("Status", "queueStatus"),
                        new Stat("In queue", "queueCount"),
                        new Button("Leave Queue", "leaveQueue", Map.of()))));
    }
}
