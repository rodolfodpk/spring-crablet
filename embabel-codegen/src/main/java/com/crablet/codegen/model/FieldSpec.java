package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public record FieldSpec(
        @JsonProperty("name") String name,
        @JsonProperty("type") String type,
        @JsonProperty("validation") @JsonDeserialize(using = FieldSpec.ValidationDeserializer.class) List<String> validation
) {
    public FieldSpec {
        type = (type == null) ? "String" : type;
        validation = (validation == null) ? List.of() : validation;
    }

    public String javaType() {
        return switch (type) {
            case "int" -> "int";
            case "long" -> "long";
            case "boolean" -> "boolean";
            case "BigDecimal" -> "java.math.BigDecimal";
            case "UUID" -> "java.util.UUID";
            case "Instant" -> "java.time.Instant";
            default -> "String";
        };
    }

    public String yaviMethod() {
        return switch (type) {
            case "int" -> "_integer";
            case "long" -> "_long";
            case "boolean" -> "_boolean";
            case "BigDecimal" -> "_bigDecimal";
            default -> "_string";
        };
    }

    static class ValidationDeserializer extends StdDeserializer<List<String>> {
        ValidationDeserializer() {
            super(List.class);
        }

        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            List<String> result = new ArrayList<>();
            if (p.currentToken() == JsonToken.START_ARRAY) {
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    result.add(p.getText());
                }
            } else {
                result.add(p.getText());
            }
            return result;
        }
    }
}
