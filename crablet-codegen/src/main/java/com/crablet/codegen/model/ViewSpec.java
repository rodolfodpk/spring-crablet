package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ViewSpec(
        @JsonProperty("name") String name,
        @JsonProperty("reads") List<String> reads,
        @JsonProperty("tag") String tag,
        @JsonProperty("fields") List<FieldSpec> fields
) {
    public ViewSpec {
        reads = (reads == null) ? List.of() : reads;
        fields = (fields == null) ? List.of() : fields;
    }

    public String tableName() {
        return name.replaceAll("([A-Z])", "_$1").toLowerCase().replaceFirst("^_", "");
    }
}
