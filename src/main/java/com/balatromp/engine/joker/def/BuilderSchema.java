package com.balatromp.engine.joker.def;

import com.balatromp.engine.card.Edition;
import com.balatromp.engine.card.Enhancement;
import com.balatromp.engine.card.Seal;
import com.balatromp.engine.card.Suit;
import com.balatromp.engine.hand.HandType;
import com.balatromp.engine.joker.Trigger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The building-block vocabulary the joker builder UI renders as dropdowns. The
 * value lists are derived from the real engine enums, so the UI can never offer a
 * suit, hand type, trigger, condition, or effect the interpreter doesn't
 * understand — the form stays in lockstep with {@link Condition}/{@link Value}/
 * {@link EffectTemplate} automatically. The UI knows how to render each
 * condition/value type's parameters; this just enumerates the choices.
 */
public final class BuilderSchema {

    private BuilderSchema() {}

    public static Map<String, Object> build() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("triggers", names(Trigger.values()));
        s.put("conditionTypes", List.of(
                "always", "scoredSuit", "scoredParity", "scoredIsFace", "scoredRankBetween",
                "scoredEnhancement", "scoredEdition", "scoredSeal", "handContainsPair", "handIs",
                "playedCount", "discardedFaceCount", "scoringAnyFace", "consumableType",
                "stateAtLeast", "moneyAtLeast", "handsLeft", "discardsLeft", "ante", "and", "or", "not"));
        s.put("effectOps", names(EffectTemplate.Op.values()));
        s.put("valueTypes", List.of("const", "state", "count", "runVar"));
        s.put("mutationOps", names(Mutation.Op.values()));

        Map<String, Object> enums = new LinkedHashMap<>();
        enums.put("suit", names(Suit.values()));
        enums.put("enhancement", names(Enhancement.values()));
        enums.put("edition", names(Edition.values()));
        enums.put("seal", names(Seal.values()));
        enums.put("handType", names(HandType.values()));
        enums.put("cmp", names(Condition.Cmp.values()));
        enums.put("countSource", names(Value.Source.values()));
        enums.put("runVar", names(Value.Var.values()));
        s.put("enums", enums);

        s.put("rarities", List.of("Common", "Uncommon", "Rare", "Legendary"));
        return s;
    }

    private static List<String> names(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }
}
