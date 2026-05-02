package com.crablet.codegen.llm;

record CodegenLlmSelection(
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        int maxTokens,
        boolean requiresApiKey) {
}
