package com.balatro.engine.joker.def;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;

/**
 * A thing an effect or {@link Modify} operates on, named by the game's actual nouns instead of one flat bag.
 * Today: {@link Hand} (size / plays / discards / draw count) and the run-economy-shop variables still in
 * {@link Value.Var}. Both are {@code Subject}s; a modifier or a run-state read targets one.
 *
 * <p>Serializes as a flat name string (a concrete enum value writes its {@code name()}), with no polymorphic
 * wrapper — the deserializer resolves the name across the subject enums, which keeps every authored
 * {@code Modify}/{@code RunVar} in the content JSON as a clean {@code "variable": "SIZE"} rather than a typed
 * object. Names are unique across the enums, so the lookup is unambiguous.
 */
@JsonDeserialize(using = Subject.Deser.class)
public interface Subject {

    /** The constant's name — provided by every enum that implements this. */
    String name();

    /** Resolve a serialized subject name back to its enum value (Hand first, then the Var bag). */
    static Subject byName(String s) {
        try {
            return Hand.valueOf(s);
        } catch (IllegalArgumentException notHand) {
            return Value.Var.valueOf(s);
        }
    }

    final class Deser extends JsonDeserializer<Subject> {
        @Override
        public Subject deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return byName(p.getValueAsString());
        }
    }
}
