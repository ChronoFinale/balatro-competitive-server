package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.engine.joker.def.JokerDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The joker half of the safety net — and the guard on the DSL-completeness invariant. A data joker must
 * DO something; a JokerDef with no rules, hand-mods, var-mods, run-mod capability or copy spec is a silent
 * no-op (an empty {@code behaviorInCode()} def). The proven result of the expressiveness audit is that the
 * ONLY built-in jokers with an empty def are the two PvP/Match holdouts — Pizza and Speedrun — whose effect
 * is genuinely match-coordinated plumbing, not rule-shaped content. {@link #BEHAVIOR_IN_CODE} pins exactly
 * that set, so a new empty-def joker (a DSL regression) or a stale whitelist entry fails the build.
 *
 * <p>(Penny Pincher is NOT here: it carries a real {@code runMod} — data-declared, Run-applied — so it has
 * a wired effect. Hand-coded jokers aren't DataJokers; their effect lives in the class.)
 */
class JokerCoverageTest {

    /** The complete set of built-in jokers whose def carries no DSL data — match-coordinated PvP plumbing.
     *  (Speedrun is now data: on(PVP_BLIND_REACHED).when(reachedPvpFirst).create(SPECTRAL).) */
    private static final Set<String> BEHAVIOR_IN_CODE = Set.of("j_pizza");

    private static boolean hasWiredEffect(JokerDef d) {
        return !d.rules().isEmpty() || !d.handMods().isEmpty() || !d.mods().isEmpty()
                || !d.runMod().isNone() || d.copy() != null;
    }

    @Test
    void theOnlyUnwiredJokersAreThePvpHoldouts() {
        Set<String> emptyDef = new TreeSet<>();
        for (String key : JokerLibrary.builtinKeys()) {
            if (!(JokerLibrary.create(key) instanceof DataJoker dj)) continue; // native joker — wired in class
            if (!hasWiredEffect(dj.def())) emptyDef.add(key);
        }
        // Exact match pins the invariant both ways: a new empty-def joker (wire it as data or justify it as
        // PvP plumbing) AND a stale entry (a holdout that's since been expressed as data) both fail here.
        assertThat(emptyDef)
                .as("the DSL expresses every joker as data except the PvP/Match holdouts — keep this exact")
                .containsExactlyInAnyOrderElementsOf(new ArrayList<>(BEHAVIOR_IN_CODE));
    }
}
