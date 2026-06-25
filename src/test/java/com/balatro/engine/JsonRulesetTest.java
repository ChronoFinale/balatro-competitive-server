package com.balatro.engine;

import static com.balatro.engine.TestSupport.c;
import static com.balatro.engine.TestSupport.score;
import static com.balatro.engine.card.Rank.KING;
import static com.balatro.engine.card.Suit.DIAMONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.balatro.engine.card.Card;
import com.balatro.engine.card.Suit;
import com.balatro.dsl.Cond;
import com.balatro.grammar.Condition;
import com.balatro.engine.joker.def.DataJoker;
import com.balatro.grammar.JokerDef;
import com.balatro.dsl.Jokers;
import com.balatro.grammar.Effect;
import com.balatro.dsl.Val;
import com.balatro.grammar.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The payoff of "the records are the source of truth": a card is <b>data</b>. A joker authored in code
 * and the same joker serialized to JSON and loaded back score identically — and a brand-new joker written
 * as raw JSON (a modder, a builder UI, a downloaded ruleset) runs on the authoritative server with zero
 * code. This is what makes "load a ruleset from JSON" real, not aspirational.
 */
class JsonRulesetTest {

    private final ObjectMapper json = new ObjectMapper();

    // A pair of Diamond Kings: both score, both Diamonds. 30 chips, base 2 Mult.
    private static final List<Card> TWO_DIAMONDS = List.of(c(KING, DIAMONDS), c(KING, DIAMONDS));

    private static double scoreOf(JokerDef def) {
        return score(List.of(new DataJoker(def)), TWO_DIAMONDS).score();
    }

    @Test
    void aJokerRoundTripsThroughJsonAndScoresIdentically() throws Exception {
        JokerDef built = Jokers.uncommon("j_dynamo", "Diamond Dynamo").cost(6)
                .desc("+5 Mult per scored Diamond")
                .prop("mult", 5)
                .forEachScored(Cond.card().suit(Suit.DIAMONDS)).add(Effect.Term.MULT, Val.prop("mult"))
                .build();

        String asJson = json.writeValueAsString(built);
        JokerDef loaded = json.readValue(asJson, JokerDef.class);

        // +5 per Diamond x2 -> 30 x (2 + 10) = 360, and the loaded copy is bit-identical in score.
        assertThat(scoreOf(built)).isEqualTo(30.0 * (2 + 5 + 5));
        assertThat(scoreOf(loaded)).isEqualTo(scoreOf(built));
    }

    @Test
    void theUnifiedCompareConditionSerializesAsOneTypeAndRoundTrips() throws Exception {
        // Guards the read-side cleanup: MoneyAtLeast/HandsLeft/Ante/... collapsed into one Compare,
        // so the serialized form is a single "compare" type, not six bespoke ones.
        JokerDef built = Jokers.common("j_rich_test", "Rich Test").cost(3)
                .desc("+10 Mult if you have $20 or more")
                .whenHand(Cond.runVar(Value.Var.MONEY).atLeast(20)).add(Effect.Term.MULT, 10)
                .build();

        String asJson = json.writeValueAsString(built);
        assertThat(asJson).contains("\"type\":\"compare\"");
        JokerDef loaded = json.readValue(asJson, JokerDef.class);
        assertThat(loaded.rules().get(0).condition()).isInstanceOf(Condition.Compare.class);
    }

    @Test
    void theSuitTargetTwinSerializesAsScoredSuitWithATargetKey() throws Exception {
        // Guards the twin merge: ScoredSuitIsTarget folded into ScoredSuit(suit=null, targetKey=...).
        JokerDef built = Jokers.uncommon("j_ancient_test", "Ancient Test").cost(8)
                .desc("Each played card of the round's suit gives x1.5 Mult")
                .forEachScored(Cond.card().suitIsTarget("ancientSuit")).multiply(Effect.Term.MULT, 1.5)
                .build();

        JokerDef loaded = json.readValue(json.writeValueAsString(built), JokerDef.class);
        Condition.ScoredSuit ss = (Condition.ScoredSuit) loaded.rules().get(0).condition();
        assertThat(ss.suit()).isNull();
        assertThat(ss.targetKey()).isEqualTo("ancientSuit");
    }

    @Test
    void aBrandNewJokerAuthoredAsRawJsonRunsOnTheServer() throws Exception {
        // No code — a modder/builder/ruleset writes this and the authoritative server scores it.
        String raw = """
            {
              "key": "j_custom_dynamo",
              "name": "Custom Dynamo",
              "description": "+5 Mult per scored Diamond",
              "rarity": "Uncommon",
              "cost": 6,
              "blueprintCompatible": true,
              "props": { "mult": 5 },
              "rules": [
                {
                  "when": "ON_SCORED",
                  "condition": { "type": "scoredSuit", "suit": "DIAMONDS" },
                  "effects": [ { "type": "score", "op": "ADD", "term": "MULT", "value": { "type": "prop", "name": "mult" } } ]
                }
              ]
            }
            """;

        JokerDef def = json.readValue(raw, JokerDef.class);

        assertThat(def.key()).isEqualTo("j_custom_dynamo");
        assertThat(scoreOf(def)).isEqualTo(30.0 * (2 + 5 + 5)); // +5 per Diamond, scored by the real engine
    }

    @Test
    void anEnumValuedPropHasTheSameShapeBeforeAndAfterAJsonRoundTrip() throws Exception {
        // An enum prop (a Suit passed in code) is normalized to its name, so a load doesn't silently
        // change Suit.DIAMONDS into the String "DIAMONDS" only on the round-tripped copy.
        JokerDef built = Jokers.uncommon("j_suited", "Suited").cost(5).desc("metadata prop")
                .prop("suit", Suit.DIAMONDS)
                .forEachScored(Cond.card().suit(Suit.DIAMONDS)).add(Effect.Term.MULT, Val.of(1))
                .build();

        assertThat(built.props().get("suit")).isEqualTo("DIAMONDS"); // stored as a String in-memory
        JokerDef loaded = json.readValue(json.writeValueAsString(built), JokerDef.class);
        assertThat(loaded.props()).isEqualTo(built.props()); // identical shape, no String/enum drift
    }

    @Test
    void blueprintCompatibilityDefaultsTrueWhenOmittedFromJson() throws Exception {
        // Omitting the flag shouldn't silently make a joker un-copyable; the sensible default is true.
        String raw = """
            { "key": "j_omit", "name": "Omit", "description": "x", "rarity": "Common", "cost": 2 }
            """;
        assertThat(json.readValue(raw, JokerDef.class).blueprintCompatible()).isTrue();

        String explicitFalse = """
            { "key": "j_no", "name": "No", "description": "x", "rarity": "Common", "cost": 2,
              "blueprintCompatible": false }
            """;
        assertThat(json.readValue(explicitFalse, JokerDef.class).blueprintCompatible()).isFalse();
    }
}
