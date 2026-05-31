package com.crablet.codegen.pipeline;

import java.util.List;

public record CompileResult(boolean success, List<CompileError> errors) {
    public static CompileResult ok() {
        return new CompileResult(true, List.of());
    }
}
