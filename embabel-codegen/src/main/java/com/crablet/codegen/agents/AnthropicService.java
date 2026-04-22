package com.crablet.codegen.agents;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class AnthropicService {

    private final String apiKey;
    private final String model;
    private final long maxTokens;
    private AnthropicClient client;

    public AnthropicService(
            @Value("${codegen.anthropic.api-key:}") String apiKey,
            @Value("${codegen.anthropic.model:claude-sonnet-4-6}") String model,
            @Value("${codegen.anthropic.max-tokens:8096}") long maxTokens) {
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    private AnthropicClient client() {
        if (client == null) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException(
                        "ANTHROPIC_API_KEY is not set. Export it or set codegen.anthropic.api-key in application.yml.");
            }
            client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        }
        return client;
    }

    public String complete(String systemPrompt, String userPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                .build();
        Message response = client().messages().create(params);
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining("\n"));
    }
}
