package com.balatro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.joker.Joker;
import com.balatro.engine.joker.JokerLibrary;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.engine.joker.def.JokerDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The joker half of the safety net: every data joker must DO something — a JokerDef with no rules,
 * mutations, hand-mods, run-mod capability or copy spec is a silent no-op. The handful whose effect
 * legitimately lives in a derived config / Run code (not the def) are listed in {@link #HANDLED_ELSEWHERE};
 * anything else with an empty def fails here. (Hand-coded jokers aren't DataJokers; effect lives in class.)
 */
class JokerCoverageTest {

    /** Effect lives in a derived config or Run/Match/IntentHandler code, not the JokerDef. */
    private static final Set<String> HANDLED_ELSEWHERE = Set.of(
            "j_showman", "j_astronomer",              // ShopConfig (shop rules); Chaos is now a FREE_REROLLS RunMod
            "j_to_the_moon",                          // EconomyConfig (interest formula; Credit Card is a MIN_MONEY mod)
            "j_penny_pincher",                        // Run (Nemesis shop-entry economy)
            "j_pizza", "j_speedrun");                 // Match (Nemesis/PvP)

    @Test
    void everyDataJokerHasAWiredEffect() {
        List<String> emptyNoOp = new ArrayList<>();
        for (String key : JokerLibrary.builtinKeys()) {
            Joker j = JokerLibrary.create(key);
            if (!(j instanceof DataJoker dj)) continue; // native joker — wired in code
            JokerDef d = dj.def();
            boolean hasEffect = !d.rules().isEmpty() || !d.mutations().isEmpty()
                    || !d.handMods().isEmpty() || !d.mods().isEmpty() || !d.runMod().isNone() || d.copy() != null;
            if (!hasEffect && !HANDLED_ELSEWHERE.contains(key)) emptyNoOp.add(key);
        }
        assertThat(emptyNoOp).as("data jokers with no wired effect — wire them or add to HANDLED_ELSEWHERE").isEmpty();
    }
}
