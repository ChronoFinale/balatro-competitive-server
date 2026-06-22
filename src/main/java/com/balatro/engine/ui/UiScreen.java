package com.balatro.engine.ui;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A named screen: a title + an ordered list of {@link UiComponent}s a generic renderer lays out. */
public record UiScreen(String id, String title, List<UiComponent> components) {

    @JsonCreator
    public UiScreen(@JsonProperty("id") String id, @JsonProperty("title") String title,
                    @JsonProperty("components") List<UiComponent> components) {
        this.id = id;
        this.title = title;
        this.components = components == null ? List.of() : List.copyOf(components);
    }
}
