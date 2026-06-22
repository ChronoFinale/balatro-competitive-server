package com.balatro.engine.ui;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

/**
 * The closed UI vocabulary — a screen's components as <b>data</b>, the same way scoring is data. A generic
 * renderer (the Electron client now, a Lua renderer later) interprets these; the server/spec decides WHAT to
 * show, the client only renders it. This keeps the thin-client model all the way up to the lobby/selection UX:
 * a new screen or flow is authored once and rendered everywhere, never reimplemented per platform.
 *
 * <p>{@link Button#intent} names a wire intent (e.g. {@code newRun}); {@link Button#params} values may
 * reference a {@link Select#bind} state key with a {@code $name} placeholder, which the renderer resolves
 * from the screen's local state before sending. {@link Select#source} names a data list the client already
 * has (e.g. {@code "rulesets"} from auth, {@code "decks"} from synced content).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UiComponent.Label.class, name = "label"),
    @JsonSubTypes.Type(value = UiComponent.Select.class, name = "select"),
    @JsonSubTypes.Type(value = UiComponent.Button.class, name = "button"),
    @JsonSubTypes.Type(value = UiComponent.Stat.class, name = "stat"),
})
public sealed interface UiComponent {

    /** Static (or templated) text. */
    record Label(String text) implements UiComponent {}

    /** A dropdown bound to a client data {@code source}, storing the choice under the {@code bind} state key. */
    record Select(String id, String label, String source, String bind) implements UiComponent {}

    /** A button that sends {@code intent} with {@code params} ({@code $name} values resolved from state). */
    record Button(String text, String intent, Map<String, Object> params) implements UiComponent {}

    /** A live key/value display, reading {@code source} from the client's view/session state. */
    record Stat(String label, String source) implements UiComponent {}
}
