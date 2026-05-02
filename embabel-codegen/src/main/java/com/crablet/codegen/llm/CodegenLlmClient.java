package com.crablet.codegen.llm;

public interface CodegenLlmClient {

    String complete(String systemPrompt, String userPrompt);
}
