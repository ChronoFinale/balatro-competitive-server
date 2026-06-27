package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the preview-mirror invariant. {@code src/main/resources/public/preview.js} hand-reimplements the
 * scoring interpreter in JavaScript so the client can preview scores live. That's the same {@code Condition}/
 * {@code Value} logic written TWICE (Java + JS) — and a new grammar subtype added on the server without a
 * preview handler would <b>silently</b> fall through preview.js's {@code default} (returning null/0) with no
 * error, degrading the preview for jokers that use it.
 *
 * <p>This test fails the build when a {@code Condition}/{@code Value} subtype is neither handled by a
 * {@code case '<name>':} in preview.js nor listed in {@link #PREVIEW_UNMIRRORED_BASELINE}. So the Java↔JS
 * duplication can't drift unnoticed: adding a scoring word forces either a preview handler (+ a fixture in
 * {@code preview.test.mjs}, whose 347 cases verify the handlers are CORRECT) or a conscious, documented entry
 * here. Mirrors {@code DslRailsTest}'s reflection-over-{@code @JsonSubTypes} approach.
 */
class PreviewMirrorCoverageTest {

    /**
     * Condition/Value subtypes NOT handled in preview.js today — the preview falls back to the authoritative
     * server for any joker using them. Two kinds, both acceptable as a baseline:
     * <ul>
     *   <li><b>Inherently non-scoring</b> (fired only at discard/blind-select/end-of-round/PvP/consumable
     *       triggers, so they never affect a played-hand count-up): {@code discardedFaceCount},
     *       {@code consumableType}, {@code bossBlindSelected}, {@code bossAbilityActive}, {@code reachedPvpFirst}.</li>
     *   <li><b>Scoring but unmirror'd</b> (the preview is degraded → server fallback; could be added later):
     *       {@code roundHandTypeConsistent} (Card Sharp), {@code playedHandIsMostPlayed} (Obelisk),
     *       {@code scoredPlayedThisAnte}; {@code diff} (Skip-Off, PvP).</li>
     * </ul>
     * A NEW scoring subtype must be mirrored, not added here.
     */
    private static final Set<String> PREVIEW_UNMIRRORED_BASELINE = Set.of(
            "discardedFaceCount", "consumableType", "bossBlindSelected", "bossAbilityActive", "reachedPvpFirst",
            "roundHandTypeConsistent", "playedHandIsMostPlayed", "scoredPlayedThisAnte", "diff");

    @Test
    void everyConditionAndValueWordIsMirroredOrBaselined() throws IOException {
        Set<String> mirrored = previewCaseLabels();
        List<String> drifted = new ArrayList<>();
        for (String name : conditionAndValueSubtypes()) {
            if (!mirrored.contains(name) && !PREVIEW_UNMIRRORED_BASELINE.contains(name)) drifted.add(name);
        }
        assertThat(drifted)
                .as("grammar words interpreted server-side but NOT mirrored in preview.js (silent preview "
                        + "drift) — add a `case '<name>':` to preview.js + a preview.test.mjs fixture, or "
                        + "consciously add it to PREVIEW_UNMIRRORED_BASELINE with a reason")
                .isEmpty();
    }

    @Test
    void baselineHasNoStaleEntries() throws IOException {
        Set<String> mirrored = previewCaseLabels();
        Set<String> all = new HashSet<>(conditionAndValueSubtypes());
        List<String> stale = PREVIEW_UNMIRRORED_BASELINE.stream()
                .filter(n -> !all.contains(n) || mirrored.contains(n)).sorted().toList();
        assertThat(stale)
                .as("PREVIEW_UNMIRRORED_BASELINE entries that no longer exist or are now mirrored — remove them")
                .isEmpty();
    }

    private static List<String> conditionAndValueSubtypes() {
        List<String> names = new ArrayList<>();
        for (Class<?> root : List.of(com.balatro.grammar.Condition.class, com.balatro.grammar.Value.class)) {
            for (JsonSubTypes.Type t : root.getAnnotation(JsonSubTypes.class).value()) names.add(t.name());
        }
        return names;
    }

    private static Set<String> previewCaseLabels() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/public/preview.js"));
        Matcher m = Pattern.compile("case '([a-zA-Z][a-zA-Z0-9]*)'").matcher(js);
        Set<String> labels = new HashSet<>();
        while (m.find()) labels.add(m.group(1));
        return labels;
    }
}
