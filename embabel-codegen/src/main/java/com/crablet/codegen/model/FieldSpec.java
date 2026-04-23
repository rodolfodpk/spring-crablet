package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FieldSpec(
        @JsonProperty("name") String name,
        @JsonProperty("type") String type,
        @JsonProperty("minimum") Integer minimum,
        @JsonProperty("exclusiveMinimum") Integer exclusiveMinimum,
        @JsonProperty("maximum") Integer maximum,
        @JsonProperty("exclusiveMaximum") Integer exclusiveMaximum,
        @JsonProperty("minLength") Integer minLength,
        @JsonProperty("maxLength") Integer maxLength,
        @JsonProperty("items") FieldSpec items,
        @JsonProperty("additionalProperties") FieldSpec additionalProperties,
        @JsonProperty("minItems") Integer minItems,
        @JsonProperty("maxItems") Integer maxItems
) {
    public FieldSpec {
        type = (type == null) ? "string" : type;
    }

    public String javaType() {
        return switch (type) {
            case "array" -> "List<" + boxed(items != null ? items.javaType() : "Object") + ">";
            case "map"   -> "Map<String, " + boxed(additionalProperties != null ? additionalProperties.javaType() : "Object") + ">";
            case "integer", "int" -> "int";
            case "long" -> "long";
            case "boolean" -> "boolean";
            case "number", "BigDecimal" -> "java.math.BigDecimal";
            case "UUID" -> "java.util.UUID";
            case "Instant" -> "java.time.Instant";
            default -> "String";
        };
    }

    public String displayType() {
        return switch (type) {
            case "array" -> "array<" + (items != null ? items.type() : "object") + ">";
            case "map"   -> "map<string," + (additionalProperties != null ? additionalProperties.type() : "object") + ">";
            default -> type;
        };
    }

    public String yaviMethod() {
        return switch (type) {
            case "integer", "int" -> "_integer";
            case "long" -> "_long";
            case "boolean" -> "_boolean";
            case "number", "BigDecimal" -> "_bigDecimal";
            default -> "_string";
        };
    }

    /**
     * Returns the YAVI builder constraint chain segment for this field's JSON Schema constraints.
     * Empty string when no constraints are defined.
     */
    public String yaviConstraints() {
        StringBuilder sb = new StringBuilder();
        if ("array".equals(type) || "map".equals(type)) {
            if (minItems != null) sb.append(".greaterThanOrEqualTo(").append(minItems).append(")");
            if (maxItems != null) sb.append(".lessThanOrEqualTo(").append(maxItems).append(")");
            return sb.toString();
        }
        if (exclusiveMinimum != null) sb.append(".greaterThan(").append(exclusiveMinimum).append(")");
        if (minimum != null)          sb.append(".greaterThanOrEqual(").append(minimum).append(")");
        if (exclusiveMaximum != null) sb.append(".lessThan(").append(exclusiveMaximum).append(")");
        if (maximum != null)          sb.append(".lessThanOrEqual(").append(maximum).append(")");
        if (minLength != null && minLength == 1 && maxLength == null) {
            sb.append(".notBlank()");
        } else {
            if (minLength != null) sb.append(".greaterThanOrEqualTo(").append(minLength).append(")");
            if (maxLength != null) sb.append(".lessThanOrEqualTo(").append(maxLength).append(")");
        }
        return sb.toString();
    }

    public boolean hasConstraints() {
        return exclusiveMinimum != null || minimum != null
                || exclusiveMaximum != null || maximum != null
                || minLength != null || maxLength != null
                || minItems != null || maxItems != null;
    }

    private static String boxed(String javaType) {
        return switch (javaType) {
            case "int"     -> "Integer";
            case "long"    -> "Long";
            case "boolean" -> "Boolean";
            default        -> javaType;
        };
    }
}
