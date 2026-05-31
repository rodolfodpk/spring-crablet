package com.crablet.codegen.pipeline;

import java.nio.file.Path;

public record CompileError(Path file, int line, String message) {}
