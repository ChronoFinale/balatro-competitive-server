package com.balatro.engine;

import com.balatro.engine.joker.def.Condition;
import com.balatro.engine.joker.def.Effect;
import com.balatro.engine.joker.def.Selector;
import com.balatro.engine.joker.def.Value;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every sealed vocabulary subtype must be registered in its {@code @JsonSubTypes}, or it can't round-trip
 * through JSON (the engine persists custom content as JSON; the client codegen reads the discriminators).
 * This mechanically forbids the recurring "added a Condition/Effect record but forgot the registration" bug —
 * a new permitted subclass fails this test until it's wired in.
 */
class VocabRegistrationTest {

    static Stream<Class<?>> vocabularies() {
        return Stream.of(Condition.class, Effect.class, Value.class, Selector.class);
    }

    @ParameterizedTest
    @MethodSource("vocabularies")
    void everyPermittedSubtypeIsRegistered(Class<?> vocab) {
        Set<Class<?>> registered = Arrays.stream(vocab.getAnnotation(JsonSubTypes.class).value())
                .map(JsonSubTypes.Type::value)
                .collect(Collectors.toSet());
        for (Class<?> sub : vocab.getPermittedSubclasses()) {
            assertThat(registered)
                    .as("%s is a permitted subtype of %s but missing from @JsonSubTypes",
                            sub.getSimpleName(), vocab.getSimpleName())
                    .contains(sub);
        }
    }
}
