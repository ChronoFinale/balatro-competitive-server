package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The DSL rails, asserted as code so the philosophy can't silently erode (docs/DSL.md). Reflects over the
 * whole {@code com.balatro.grammar} package — the pure-data grammar — and enforces:
 * <ul>
 *   <li><b>No behavior in grammar records</b> — a record carries only its accessors, {@code Object} overrides,
 *       and static factory builders. Decision logic lives in {@code engine.eval}, never in the data.</li>
 *   <li><b>Closed sets are enums, not Strings</b> — a record component is a {@code String} only when it is a
 *       genuine free-form identifier (key/name/sprite-url/seed-key); a closed set must be an enum.</li>
 *   <li><b>No bespoke booleans</b> — "a boolean rarely means much." A grammar record carries no boolean
 *       component outside a small documented allowlist; a new one fails this test until modeled as an enum.</li>
 * </ul>
 * (The "one model of the scoring axes" check is intentionally not asserted: {@code ReplayEntry} is the
 * client wire/animation boundary, free to keep its own vocabulary — like {@code ClientView}.)
 */
class DslRailsTest {

    /** Booleans that genuinely mean one binary thing (audited). A NEW boolean component must justify itself
     *  here or — better — become an enum. */
    private static final Set<String> ALLOWED_BOOLEANS = Set.of("dedup", "blueprintCompatible");

    /** String components that are free-form identifiers, not closed sets: keys, display names, sprite URLs,
     *  RNG seed/stream keys, joker-state variable names, binding/target keys, content tag keys. */
    private static final Set<String> ALLOWED_STRINGS = Set.of(
            "name", "spriteUrl", "spriteUrl2x", "key", "description", "var", "seedKey",
            "tag", "selfStateVar");

    /** Instance methods that are pure data PROJECTIONS (no decisions) — audited and allowed: {@code
     *  JokerDef.info()} just rebuilds a {@link JokerInfo} view from the def's own fields. */
    private static final Set<String> ALLOWED_METHODS = Set.of("info");

    @Test
    void grammarRecordsHaveNoBehavior() {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : grammarClasses()) {
            if (!c.isRecord()) continue;
            Set<String> accessors = new HashSet<>();
            for (RecordComponent rc : c.getRecordComponents()) accessors.add(rc.getName());
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue; // static factory builders are fine
                String n = m.getName();
                boolean ok = (accessors.contains(n) && m.getParameterCount() == 0)
                        || ALLOWED_METHODS.contains(n)
                        || n.equals("equals") || n.equals("hashCode") || n.equals("toString");
                if (!ok) violations.add(c.getSimpleName() + "." + n + "()");
            }
        }
        assertThat(violations)
                .as("grammar records must be PURE DATA — only accessors / Object overrides / static factories; "
                        + "move any decision logic to engine.eval")
                .isEmpty();
    }

    @Test
    void grammarRecordsUseEnumsNotStringsForClosedSets() {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : grammarClasses()) {
            if (!c.isRecord()) continue;
            for (RecordComponent rc : c.getRecordComponents()) {
                if (rc.getType() == String.class && !ALLOWED_STRINGS.contains(rc.getName())) {
                    violations.add(c.getSimpleName() + "." + rc.getName());
                }
            }
        }
        assertThat(violations)
                .as("a closed set of values must be an enum, not a String (allowed free-form ids: %s)", ALLOWED_STRINGS)
                .isEmpty();
    }

    @Test
    void grammarRecordsHaveNoBespokeBooleans() {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : grammarClasses()) {
            if (!c.isRecord()) continue;
            for (RecordComponent rc : c.getRecordComponents()) {
                if ((rc.getType() == boolean.class || rc.getType() == Boolean.class)
                        && !ALLOWED_BOOLEANS.contains(rc.getName())) {
                    violations.add(c.getSimpleName() + "." + rc.getName());
                }
            }
        }
        assertThat(violations)
                .as("a boolean rarely means much — model the choice as an enum (allowlist: %s)", ALLOWED_BOOLEANS)
                .isEmpty();
    }

    @Test
    void everyEffectAndConditionWordIsUsedByContent() {
        String content = allContentJson().replaceAll("\\s+", "");
        List<String> dead = new ArrayList<>();
        for (Class<?> root : List.of(com.balatro.grammar.Effect.class, com.balatro.grammar.Condition.class)) {
            var ann = root.getAnnotation(com.fasterxml.jackson.annotation.JsonSubTypes.class);
            for (var t : ann.value()) {
                if (!content.contains("\"type\":\"" + t.name() + "\"")) {
                    dead.add(root.getSimpleName() + "." + t.name());
                }
            }
        }
        assertThat(dead)
                .as("every Effect/Condition word must be exercised by >=1 content def — dead vocabulary means "
                        + "either a missing card or a word that should be deleted (or was a single-use verb left undecomposed)")
                .isEmpty();
    }

    /** Concatenate every compiled content/ruleset JSON so a word's discriminator ({@code "type":"<name>"}) can
     *  be searched for across all authored content (jokers, consumables, bosses, decks, tags, vouchers, ...). */
    private static String allContentJson() {
        StringBuilder sb = new StringBuilder();
        for (String d : List.of("src/main/resources/content", "src/main/resources/rulesets")) {
            Path dir = Path.of(d);
            if (!Files.isDirectory(dir)) continue;
            try (var paths = Files.walk(dir)) {
                for (Path p : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                    sb.append(Files.readString(p)).append('\n');
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return sb.toString();
    }

    /** Every record/enum compiled under {@code com.balatro.grammar} (including nested types like
     *  {@code Effect$Score}), loaded from the build output so the set is exhaustive, not hand-listed. */
    private static List<Class<?>> grammarClasses() {
        Path dir = Path.of("build/classes/java/main/com/balatro/grammar");
        assertThat(Files.isDirectory(dir)).as("grammar build output at %s", dir).isTrue();
        try (var paths = Files.walk(dir)) {
            return paths.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> "com.balatro.grammar." + dir.relativize(p).toString()
                            .replace(".class", "").replace(File.separatorChar, '.'))
                    .map(DslRailsTest::load)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable t) {
            return null;
        }
    }
}
