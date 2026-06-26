package com.balatro.grammar;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The three consumable families — a closed grammar set. Wire values are the proper-case labels (`Tarot`,
 * `Planet`, `Spectral`) used by joker triggers (Constellation counts "Planet"), pinned via {@link JsonProperty}
 * so the JSON is unchanged while the type is total.
 */
public enum ConsumableKind {
    @JsonProperty("Tarot") TAROT,
    @JsonProperty("Planet") PLANET,
    @JsonProperty("Spectral") SPECTRAL
}
