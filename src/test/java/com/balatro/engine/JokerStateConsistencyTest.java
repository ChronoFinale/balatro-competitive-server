package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.grammar.JokerDef;
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
 * class of bug the Top-Up tag was. This pins it: every state key a joker READS must either be WRITTEN by a
 * rule OR DECLARED in the def's state (via {@code .counters}/{@code .state}, which now also covers
 * engine-written keys). The def is the single source of truth — no per-joker exemption list to drift.
 */
class JokerStateConsistencyTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void everyStateReadHasAWrite() {
        List<String> problems = new ArrayList<>();
        for (JokerDef def : JokerDefLibrary.all().values()) {
            JsonNode tree = OM.valueToTree(def);
            Set<String> writes = new LinkedHashSet<>();
            Set<String> reads = new LinkedHashSet<>();
            collect(tree, writes, reads);
            // The def's declared state (counters/.state, incl. engine-written keys) counts as a write.
            def.state().keySet().forEach(writes::add);
            for (String r : reads) {
                if (!writes.contains(r)) {
                    problems.add(def.key() + ": reads state '" + r + "' but nothing writes/declares it (typo? "
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
