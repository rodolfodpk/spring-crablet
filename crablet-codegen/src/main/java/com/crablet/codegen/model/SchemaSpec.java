package com.crablet.codegen.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SchemaSpec(
        @JsonProperty("name") String name,
        @JsonProperty("fields") List<FieldSpec> fields
) {}
