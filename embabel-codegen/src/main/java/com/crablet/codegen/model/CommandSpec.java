package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record CommandSpec(
        @JsonProperty("name") String name,
        @JsonProperty("pattern") String pattern,
        @JsonProperty("produces") List<String> produces,
        @JsonProperty("guardEvents") List<String> guardEvents,
        @JsonProperty("schema") String schema,
        @JsonProperty("fields") List<FieldSpec> fields,
        @JsonProperty("validation") Map<String, Object> validation
) {
    public CommandSpec {
        produces = (produces == null) ? List.of() : produces;
        guardEvents = (guardEvents == null) ? List.of() : guardEvents;
        fields = (fields == null) ? List.of() : fields;
        validation = (validation == null) ? Map.of() : validation;
    }

    public boolean isIdempotent() { return "idempotent".equals(pattern); }
    public boolean isCommutative() { return "commutative".equals(pattern); }
    public boolean isNonCommutative() { return "non-commutative".equals(pattern); }
    public boolean hasGuard() { return !guardEvents.isEmpty(); }
}
