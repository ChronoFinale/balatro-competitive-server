package com.balatro.engine.content;

import com.balatro.engine.joker.def.BuiltinJokerDefs;
import com.balatro.engine.joker.def.JokerDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Loads the engine's content from the <b>compiled JSON artifacts</b> at startup — so the running engine is
 * driven by data files, not by the Java that authored them. The DSL ({@link BuiltinJokerDefs}) is the
 * authoring source that <i>generates</i> {@code /rulesets/vanilla.json} (via {@code ./gradlew generateContent});
 * {@code RulesetArtifactsTest} gates the two as identical, so loading from JSON can't silently diverge from
 * the source. If the artifact is absent (e.g. a stripped jar), it falls back to the in-memory DSL.
 */
public final class ContentStore {

    private ContentStore() {}

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The base joker set, loaded from {@code /rulesets/vanilla.json} (falls back to the DSL if absent). */
    public static List<JokerDef> jokers() {
        try (var in = ContentStore.class.getResourceAsStream("/rulesets/vanilla.json")) {
            if (in == null) return BuiltinJokerDefs.all();
            return List.of(JSON.readValue(in, JokerDef[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("loading /rulesets/vanilla.json", e);
        }
    }
}
