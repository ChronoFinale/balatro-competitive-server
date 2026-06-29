package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.game.TagCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The tag half of the data-driven safety net (sibling of {@link ConsumableCoverageTest} /
 * {@link JokerCoverageTest}). Tags are DATA now — each carries a {@code List<Effect>} the {@code Run}
 * interpreter resolves. So the real check is: every catalog tag actually carries an effect.
 *
 * <p>This replaces the old manual-ledger version, which only checked that each tag was <i>listed</i> in
 * a hand-maintained bucket — that rubber-stamped {@code tag_top_up} as "wired" while its effect list was
 * empty and it did nothing in game. Listing a key is not the same as the key doing something; this checks
 * the latter.
 */
class TagCoverageTest {

    /** Structural meta-tags: they act on blind/tag <i>selection</i> in {@code Run} itself, not through an
     *  {@link com.balatro.grammar.Effect}, so they are intentionally effect-less. Boss Tag
     *  rerolls the boss on arrival; Double Tag duplicates the next claimed tag. Any OTHER empty tag is a
     *  no-op bug. */
    private static final Set<String> STRUCTURAL = Set.of("tag_boss", "tag_double");

    @Test
    void everyTagDoesSomething() {
        List<String> noOps = new ArrayList<>();
        for (String key : TagCatalog.keys()) {
            if (STRUCTURAL.contains(key)) continue;
            if (TagCatalog.get(key).effects().isEmpty()) noOps.add(key);
        }
        assertThat(noOps)
                .as("tags whose effect list is empty — wire an Effect (or, if it acts on the run loop "
                        + "structurally, add it to STRUCTURAL with a reason)")
                .isEmpty();
    }

    @Test
    void everyCreateTagReferencesAKnownTag() {
        // Effect.CreateTag carries a tag KEY string; an unknown key silently times out to Timing.HELD
        // (TagCatalog.timing) instead of failing. Validate every authored CreateTag against the catalog so a
        // typo fails the build. (Tags are content, like round-targets — validated, not typed into the grammar.)
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<com.balatro.grammar.JokerDef> defs =
                new ArrayList<>(com.balatro.content.jokers.BuiltinJokerDefs.all());
        defs.addAll(com.balatro.content.jokers.BuiltinJokerDefs.mpAdditions());
        Set<String> used = new java.util.TreeSet<>();
        collectCreateTags(mapper.valueToTree(defs), used);
        Set<String> unknown = new java.util.TreeSet<>(used);
        unknown.removeAll(TagCatalog.keys());
        assertThat(unknown)
                .as("CreateTag references unknown tag key(s) (typo?) — known keys: %s", TagCatalog.keys())
                .isEmpty();
    }

    private static void collectCreateTags(com.fasterxml.jackson.databind.JsonNode node, Set<String> out) {
        if (node.isObject()) {
            if ("createTag".equals(node.path("type").asText("")) && node.has("tag")) {
                out.add(node.get("tag").asText());
            }
            node.forEach(c -> collectCreateTags(c, out));
        } else if (node.isArray()) {
            node.forEach(c -> collectCreateTags(c, out));
        }
    }

    @Test
    void structuralExemptionsAreNotStale() {
        // Each exemption must still exist AND still be genuinely effect-less — otherwise it's a tag we
        // forgot to drop from the list once it grew a real effect.
        for (String key : STRUCTURAL) {
            assertThat(TagCatalog.get(key)).as("structural tag '%s' is gone from the catalog", key).isNotNull();
            assertThat(TagCatalog.get(key).effects())
                    .as("'%s' now carries an effect — drop it from STRUCTURAL", key).isEmpty();
        }
    }
}
