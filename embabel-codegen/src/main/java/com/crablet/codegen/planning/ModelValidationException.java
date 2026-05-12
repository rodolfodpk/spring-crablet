package com.crablet.codegen.planning;

import java.util.List;

public class ModelValidationException extends IllegalArgumentException {

    private final List<String> errors;

    public ModelValidationException(List<String> errors) {
        super("event-model.yaml is not ready for generation:\n- " + String.join("\n- ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
