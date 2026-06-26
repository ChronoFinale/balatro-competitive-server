package com.balatro.engine;

import com.balatro.content.jokers.BuiltinJokerDefs;
import com.balatro.grammar.JokerDef;
import com.balatro.engine.joker.def.JokerOverlays;
import com.balatro.engine.joker.def.RulesetDiff;
import com.balatro.engine.joker.def.RulesetOverlay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The overlay model: apply (remove/override-patch/add), the reasoned diff, and the fail-fast validation. */
class RulesetOverlayTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static JokerDef base(String key) {
        return BuiltinJokerDefs.all().stream().filter(d -> d.key().equals(key)).findFirst().orElseThrow();
    }

    @Test void emptyOverlayIsIdentity() {
        List<JokerDef> base = BuiltinJokerDefs.all();
        var overlay = new RulesetOverlay("noop", "vanilla", null, null, null);
        Map<String, JokerDef> eff = JokerOverlays.apply(base, overlay);
        assertThat(eff).hasSize(base.size());
        assertThat(JokerOverlays.diff(base, overlay).entries()).isEmpty();
    }

    @Test void patchChangesOnlyNamedFields() throws Exception {
        JokerDef seltzer = base("j_seltzer");
        JokerDef patched = JokerOverlays.patch(seltzer, M.readTree("{\"rarity\":\"Rare\",\"cost\":9}"));

        assertThat(patched.rarity()).isEqualTo(com.balatro.grammar.Rarity.RARE);
        assertThat(patched.cost()).isEqualTo(9);
        // everything else carried through untouched
        assertThat(patched.key()).isEqualTo(seltzer.key());
        assertThat(patched.name()).isEqualTo(seltzer.name());
        assertThat(patched.rules()).isEqualTo(seltzer.rules());
    }

    @Test void applyAndDiffWithReasons() throws Exception {
        List<JokerDef> base = BuiltinJokerDefs.all();
        var overlay = new RulesetOverlay("demo", "vanilla",
                List.of(new RulesetOverlay.Remove("j_chicot", "PvP-broken boss disable")),
                List.of(new RulesetOverlay.Override("j_golden_ticket", "MP: Uncommon",
                        M.readTree("{\"rarity\":\"Uncommon\"}"))),
                List.of(new RulesetOverlay.Add("test joker",
                        new JokerDef("j_unit_test", "Unit Test", "x", com.balatro.grammar.Rarity.COMMON, 3, 0, 0, null, null, true, List.of()))));

        Map<String, JokerDef> eff = JokerOverlays.apply(base, overlay);
        assertThat(eff).doesNotContainKey("j_chicot");
        assertThat(eff).containsKey("j_unit_test");
        assertThat(eff.get("j_golden_ticket").rarity()).isEqualTo(com.balatro.grammar.Rarity.UNCOMMON);

        RulesetDiff diff = JokerOverlays.diff(base, overlay);
        assertThat(diff.of(RulesetDiff.Kind.REMOVED)).singleElement()
                .satisfies(e -> assertThat(e.reason()).contains("boss disable"));
        assertThat(diff.of(RulesetDiff.Kind.ADDED)).singleElement()
                .satisfies(e -> assertThat(e.key()).isEqualTo("j_unit_test"));
        assertThat(diff.of(RulesetDiff.Kind.CHANGED)).singleElement()
                .satisfies(e -> assertThat(e.fields()).contains("rarity"));
    }

    @Test void unknownTargetsFailFast() {
        List<JokerDef> base = BuiltinJokerDefs.all();
        assertThatThrownBy(() -> JokerOverlays.apply(base,
                new RulesetOverlay("x", "vanilla", List.of(new RulesetOverlay.Remove("j_nope", "typo")), null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown joker");
        assertThatThrownBy(() -> JokerOverlays.apply(base, new RulesetOverlay("x", "vanilla", null, null,
                List.of(new RulesetOverlay.Add("dup", base("j_seltzer"))))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("use override");
    }
}
