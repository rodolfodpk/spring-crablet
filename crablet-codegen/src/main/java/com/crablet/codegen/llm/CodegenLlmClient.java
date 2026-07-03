package com.crablet.codegen.llm;

import org.jspecify.annotations.NonNull;

/**
 * Dormant LLM client interface reserved for future opt-in commands (e.g. {@code crablet explain},
 * {@code crablet suggest}). Not used by the default deterministic {@code generate} pipeline.
 *
 * <p>Implementations must live in {@code com.crablet.codegen.llm} with no provider SDKs on the
 * compile classpath (enforced by the ArchUnit rule in {@code CodegenArchitectureTest}).
 */
public interface CodegenLlmClient {

    @NonNull String complete(@NonNull String systemPrompt, @NonNull String userPrompt);
}
