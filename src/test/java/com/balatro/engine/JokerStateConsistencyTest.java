package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.joker.def.JokerDef;
import com.balatro.engine.joker.def.JokerDefLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The scaling pattern ("+X, growing") is a recipe glued by a magic string: a {@code mutateState} rule
 * WRITES {@code "streak"} and a {@code state}/{@code stateStep} value READS {@code "streak"}. Nothing in
 * the type system links the two — a typo (write "streak", read "streek") is a silent no-op, the exact
 * class of bug the Top-Up tag was. This pins it: every state key a joker READS must be WRITTEN somewhere
 * (by the def, or by the engine for the keys in {@link #ENGINE_WRITTEN}). It's the safety net under the
 * Counter primitive — until scaling becomes a typed Counter, this catches the coupling breaking.
 */
class JokerStateConsistencyTest {

    private static final ObjectMapper OM = new ObjectMapper();

    /** "jokerKey:stateKey" reads that the ENGINE writes, not the joker's own rules — so a read with no
     *  def-write is legitimate. Kept tight (per-joker, not a blanket key) so it can't mask a real typo:
     *  Ceremonial Dagger's "mult" is added by Run when it eats a joker at blind select. */
    private static final Set<String> ENGINE_WRITTEN = Set.of(
            "j_ceremonial:mult");

    @Test
    void everyStateReadHasAWrite() {
        List<String> problems = new ArrayList<>();
        for (JokerDef def : JokerDefLibrary.all().values()) {
            JsonNode tree = OM.valueToTree(def);
            Set<String> writes = new LinkedHashSet<>();
            Set<String> reads = new LinkedHashSet<>();
            collect(tree, writes, reads);
            for (String r : reads) {
                if (!writes.contains(r) && !ENGINE_WRITTEN.contains(def.key() + ":" + r)) {
                    problems.add(def.key() + ": reads state '" + r + "' but nothing writes it (typo? "
                            + "writes=" + writes + ")");
                }
            }
        }
        assertThat(problems)
                .as("scaling jokers whose state read has no matching write — fix the magic-string coupling")
                .isEmpty();
    }

    private static void collect(JsonNode node, Set<String> writes, Set<String> reads) {
        if (node.isObject()) {
            String type = node.path("type").asText("");
            if (type.equals("mutateState") && node.has("var")) writes.add(node.get("var").asText());
            if ((type.equals("state") || type.equals("stateStep")) && node.has("var")) {
                reads.add(node.get("var").asText());
            }
            node.forEach(child -> collect(child, writes, reads));
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, writes, reads));
        }
    }
}
