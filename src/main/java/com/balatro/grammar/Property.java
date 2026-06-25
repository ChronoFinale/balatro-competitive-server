package com.balatro.grammar;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;

/**
 * A durable, modifiable quantity of game state — a <b>property</b> of one of the game's nouns, named by that
 * noun rather than dumped in one flat bag: {@link Hand} (size / plays / discards / draw count) and the
 * run-economy-shop properties still in {@link Value.Var}. A {@link Modify} writes a property and a condition
 * reads it; both persist across the run. (Distinct from a scoring {@code Term} — chips/mult — which is an
 * ephemeral register that exists only while one hand is being scored.)
 *
 * <p>Serializes as a flat name string (a concrete enum value writes its {@code name()}), with no polymorphic
 * wrapper — the deserializer resolves the name across the property enums, so authored {@code Modify}/{@code
 * RunVar} stay in the content JSON as a clean {@code "variable": "SIZE"} rather than a typed object. Names are
 * unique across the enums, so the lookup is unambiguous.
 */
@JsonDeserialize(using = Property.Deser.class)
public interface Property {

    /** The constant's name — provided by every enum that implements this. */
    String name();

    /** Resolve a serialized property name back to its enum value (Hand first, then the Var bag). */
    static Property byName(String s) {
        try {
            return Hand.valueOf(s);
        } catch (IllegalArgumentException notHand) {
            return Value.Var.valueOf(s);
        }
    }

    final class Deser extends JsonDeserializer<Property> {
        @Override
        public Property deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return byName(p.getValueAsString());
        }
    }
}
