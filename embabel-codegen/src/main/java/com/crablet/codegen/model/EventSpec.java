package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EventSpec(
        @JsonProperty("name") String name,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("schema") String schema,
        @JsonProperty("fields") List<FieldSpec> fields
) {
    public EventSpec {
        tags = (tags == null) ? List.of() : tags;
        fields = (fields == null) ? List.of() : fields;
    }

    public String tagConstant() {
        return tags.isEmpty() ? "" : tags.get(0).toUpperCase();
    }
}
