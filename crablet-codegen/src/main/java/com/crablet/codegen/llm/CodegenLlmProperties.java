package com.crablet.codegen.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
class CodegenLlmProperties {

    private final String provider;
    private final String llmApiKey;
    private final String llmBaseUrl;
    private final String llmModel;
    private final int maxTokens;
    private final String anthropicApiKey;
    private final String anthropicBaseUrl;
    private final String anthropicModel;
    private final String openAiApiKey;
    private final String openAiBaseUrl;
    private final String openAiModel;
    private final String deepSeekApiKey;
    private final String deepSeekBaseUrl;
    private final String deepSeekModel;
    private final String ollamaApiKey;
    private final String ollamaBaseUrl;
    private final String ollamaModel;
    private final String compatibleApiKey;
    private final String compatibleBaseUrl;
    private final String compatibleModel;

    CodegenLlmProperties(
            @Value("${codegen.llm.provider:anthropic}") String provider,
            @Value("${codegen.llm.api-key:}") String llmApiKey,
            @Value("${codegen.llm.base-url:}") String llmBaseUrl,
            @Value("${codegen.llm.model:}") String llmModel,
            @Value("${codegen.llm.max-tokens:${codegen.anthropic.max-tokens:8096}}") int maxTokens,
            @Value("${codegen.anthropic.api-key:}") String anthropicApiKey,
            @Value("${codegen.anthropic.base-url:}") String anthropicBaseUrl,
            @Value("${codegen.anthropic.model:claude-sonnet-4-6}") String anthropicModel,
            @Value("${codegen.openai.api-key:}") String openAiApiKey,
            @Value("${codegen.openai.base-url:}") String openAiBaseUrl,
            @Value("${codegen.openai.model:}") String openAiModel,
            @Value("${codegen.deepseek.api-key:}") String deepSeekApiKey,
            @Value("${codegen.deepseek.base-url:https://api.deepseek.com}") String deepSeekBaseUrl,
            @Value("${codegen.deepseek.model:deepseek-chat}") String deepSeekModel,
            @Value("${codegen.ollama.api-key:ollama}") String ollamaApiKey,
            @Value("${codegen.ollama.base-url:http://localhost:11434/v1}") String ollamaBaseUrl,
            @Value("${codegen.ollama.model:}") String ollamaModel,
            @Value("${codegen.openai-compatible.api-key:}") String compatibleApiKey,
            @Value("${codegen.openai-compatible.base-url:}") String compatibleBaseUrl,
            @Value("${codegen.openai-compatible.model:}") String compatibleModel) {
        this.provider = provider;
        this.llmApiKey = llmApiKey;
        this.llmBaseUrl = llmBaseUrl;
        this.llmModel = llmModel;
        this.maxTokens = maxTokens;
        this.anthropicApiKey = anthropicApiKey;
        this.anthropicBaseUrl = anthropicBaseUrl;
        this.anthropicModel = anthropicModel;
        this.openAiApiKey = openAiApiKey;
        this.openAiBaseUrl = openAiBaseUrl;
        this.openAiModel = openAiModel;
        this.deepSeekApiKey = deepSeekApiKey;
        this.deepSeekBaseUrl = deepSeekBaseUrl;
        this.deepSeekModel = deepSeekModel;
        this.ollamaApiKey = ollamaApiKey;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.ollamaModel = ollamaModel;
        this.compatibleApiKey = compatibleApiKey;
        this.compatibleBaseUrl = compatibleBaseUrl;
        this.compatibleModel = compatibleModel;
    }

    CodegenLlmSelection selection() {
        String selectedProvider = providerName();
        return switch (selectedProvider) {
            case "anthropic" -> validate(new CodegenLlmSelection(
                    selectedProvider,
                    firstText(llmModel, anthropicModel),
                    firstText(llmApiKey, anthropicApiKey),
                    firstText(llmBaseUrl, anthropicBaseUrl),
                    maxTokens,
                    true
            ));
            case "openai" -> validate(new CodegenLlmSelection(
                    selectedProvider,
                    firstText(llmModel, openAiModel),
                    firstText(llmApiKey, openAiApiKey),
                    firstText(llmBaseUrl, openAiBaseUrl),
                    maxTokens,
                    true
            ));
            case "deepseek" -> validate(new CodegenLlmSelection(
                    selectedProvider,
                    firstText(llmModel, deepSeekModel),
                    firstText(llmApiKey, deepSeekApiKey),
                    firstText(llmBaseUrl, deepSeekBaseUrl),
                    maxTokens,
                    true
            ));
            case "ollama" -> validate(new CodegenLlmSelection(
                    selectedProvider,
                    firstText(llmModel, ollamaModel),
                    firstText(llmApiKey, ollamaApiKey),
                    firstText(llmBaseUrl, ollamaBaseUrl),
                    maxTokens,
                    false
            ));
            case "openai-compatible", "compatible", "local", "custom" -> validate(new CodegenLlmSelection(
                    "openai-compatible",
                    firstText(llmModel, compatibleModel),
                    firstText(llmApiKey, compatibleApiKey),
                    firstText(llmBaseUrl, compatibleBaseUrl),
                    maxTokens,
                    false
            ));
            default -> throw new IllegalStateException(
                    "Unsupported codegen LLM provider '%s'. Use anthropic, openai, deepseek, ollama, or openai-compatible."
                            .formatted(provider));
        };
    }

    private String providerName() {
        String value = firstText(provider, "anthropic");
        return value.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private CodegenLlmSelection validate(CodegenLlmSelection selection) {
        if (isBlank(selection.model())) {
            throw new IllegalStateException(
                    "No model configured for codegen LLM provider '%s'. Set codegen.llm.model or the provider-specific model property."
                            .formatted(selection.provider()));
        }
        if (selection.requiresApiKey() && isBlank(selection.apiKey())) {
            throw new IllegalStateException(missingKeyMessage(selection.provider()));
        }
        if ("openai-compatible".equals(selection.provider()) && isBlank(selection.baseUrl())) {
            throw new IllegalStateException(
                    "No base URL configured for codegen LLM provider 'openai-compatible'. Set codegen.llm.base-url or codegen.openai-compatible.base-url.");
        }
        return selection;
    }

    private String missingKeyMessage(String provider) {
        return switch (provider) {
            case "anthropic" ->
                    "ANTHROPIC_API_KEY is not set. Export it or set codegen.anthropic.api-key / codegen.llm.api-key.";
            case "openai" ->
                    "OPENAI_API_KEY is not set. Export it or set codegen.openai.api-key / codegen.llm.api-key.";
            case "deepseek" ->
                    "DEEPSEEK_API_KEY is not set. Export it or set codegen.deepseek.api-key / codegen.llm.api-key.";
            default ->
                    "API key is not set for codegen LLM provider '%s'. Set codegen.llm.api-key or the provider-specific key."
                            .formatted(provider);
        };
    }

    private static String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (!isBlank(candidate)) return candidate.trim();
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
