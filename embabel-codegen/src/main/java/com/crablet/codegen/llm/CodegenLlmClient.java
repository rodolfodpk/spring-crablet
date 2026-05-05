package com.crablet.codegen.llm;

import org.jspecify.annotations.NonNull;

public interface CodegenLlmClient {

    @NonNull String complete(@NonNull String systemPrompt, @NonNull String userPrompt);
}
