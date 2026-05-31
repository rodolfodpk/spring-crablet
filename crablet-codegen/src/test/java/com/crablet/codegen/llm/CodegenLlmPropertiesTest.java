package com.crablet.codegen.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodegenLlmPropertiesTest {

    @Test
    void anthropicKeepsBackwardCompatibleDefaults() {
        CodegenLlmSelection selection = properties(
                "anthropic",
                "", "", "",
                "sk-ant", "", "claude-sonnet-4-6",
                "", "", "",
                "", "", "deepseek-chat",
                "ollama", "http://localhost:11434/v1", "",
                "", "", ""
        ).selection();

        assertThat(selection.provider()).isEqualTo("anthropic");
        assertThat(selection.apiKey()).isEqualTo("sk-ant");
        assertThat(selection.model()).isEqualTo("claude-sonnet-4-6");
        assertThat(selection.requiresApiKey()).isTrue();
    }

    @Test
    void openAiUsesProviderSpecificKeyAndModel() {
        CodegenLlmSelection selection = properties(
                "openai",
                "", "", "",
                "sk-ant", "", "claude-sonnet-4-6",
                "sk-openai", "", "gpt-test",
                "", "", "deepseek-chat",
                "ollama", "http://localhost:11434/v1", "",
                "", "", ""
        ).selection();

        assertThat(selection.provider()).isEqualTo("openai");
        assertThat(selection.apiKey()).isEqualTo("sk-openai");
        assertThat(selection.model()).isEqualTo("gpt-test");
        assertThat(selection.requiresApiKey()).isTrue();
    }

    @Test
    void deepSeekUsesOpenAiCompatibleNamedProviderConfig() {
        CodegenLlmSelection selection = properties(
                "deepseek",
                "", "", "",
                "sk-ant", "", "claude-sonnet-4-6",
                "", "", "",
                "sk-deepseek", "https://api.deepseek.com", "deepseek-chat",
                "ollama", "http://localhost:11434/v1", "",
                "", "", ""
        ).selection();

        assertThat(selection.provider()).isEqualTo("deepseek");
        assertThat(selection.apiKey()).isEqualTo("sk-deepseek");
        assertThat(selection.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(selection.model()).isEqualTo("deepseek-chat");
    }

    @Test
    void localOpenAiCompatibleEndpointDoesNotRequireCloudKeys() {
        CodegenLlmSelection selection = properties(
                "openai-compatible",
                "", "", "",
                "", "", "claude-sonnet-4-6",
                "", "", "",
                "", "", "deepseek-chat",
                "ollama", "http://localhost:11434/v1", "",
                "", "http://localhost:11434/v1", "qwen2.5-coder:32b"
        ).selection();

        assertThat(selection.provider()).isEqualTo("openai-compatible");
        assertThat(selection.apiKey()).isBlank();
        assertThat(selection.baseUrl()).isEqualTo("http://localhost:11434/v1");
        assertThat(selection.model()).isEqualTo("qwen2.5-coder:32b");
        assertThat(selection.requiresApiKey()).isFalse();
    }

    @Test
    void unknownProviderFailsClearly() {
        assertThatThrownBy(() -> properties(
                "bogus",
                "", "", "",
                "sk-ant", "", "claude-sonnet-4-6",
                "", "", "",
                "", "", "deepseek-chat",
                "ollama", "http://localhost:11434/v1", "",
                "", "", ""
        ).selection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported codegen LLM provider");
    }

    @Test
    void missingRequiredProviderKeyFailsWithEnvName() {
        assertThatThrownBy(() -> properties(
                "anthropic",
                "", "", "",
                "", "", "claude-sonnet-4-6",
                "", "", "",
                "", "", "deepseek-chat",
                "ollama", "http://localhost:11434/v1", "",
                "", "", ""
        ).selection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    private CodegenLlmProperties properties(
            String provider,
            String llmApiKey,
            String llmBaseUrl,
            String llmModel,
            String anthropicApiKey,
            String anthropicBaseUrl,
            String anthropicModel,
            String openAiApiKey,
            String openAiBaseUrl,
            String openAiModel,
            String deepSeekApiKey,
            String deepSeekBaseUrl,
            String deepSeekModel,
            String ollamaApiKey,
            String ollamaBaseUrl,
            String ollamaModel,
            String compatibleApiKey,
            String compatibleBaseUrl,
            String compatibleModel) {
        return new CodegenLlmProperties(
                provider,
                llmApiKey,
                llmBaseUrl,
                llmModel,
                8096,
                anthropicApiKey,
                anthropicBaseUrl,
                anthropicModel,
                openAiApiKey,
                openAiBaseUrl,
                openAiModel,
                deepSeekApiKey,
                deepSeekBaseUrl,
                deepSeekModel,
                ollamaApiKey,
                ollamaBaseUrl,
                ollamaModel,
                compatibleApiKey,
                compatibleBaseUrl,
                compatibleModel
        );
    }
}
