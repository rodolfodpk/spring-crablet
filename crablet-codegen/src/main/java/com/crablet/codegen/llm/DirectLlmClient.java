package com.crablet.codegen.llm;

import org.jspecify.annotations.NonNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
class DirectLlmClient implements CodegenLlmClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_DELAY_MS = 2_000;

    private final CodegenLlmProperties properties;

    DirectLlmClient(CodegenLlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public @NonNull String complete(@NonNull String systemPrompt, @NonNull String userPrompt) {
        CodegenLlmSelection selection = properties.selection();
        if ("anthropic".equals(selection.provider())) {
            return withRetry(() -> completeViaAnthropic(selection, systemPrompt, userPrompt));
        }
        return withRetry(() -> completeViaOpenAiCompatible(selection, systemPrompt, userPrompt));
    }

    @SuppressWarnings("unchecked")
    private String completeViaAnthropic(CodegenLlmSelection selection, String systemPrompt, String userPrompt) {
        String baseUrl = nullIfBlank(selection.baseUrl());
        if (baseUrl == null) baseUrl = "https://api.anthropic.com";
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", selection.apiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();

        Map<String, Object> body = Map.of(
                "model", selection.model(),
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt)),
                "max_tokens", selection.maxTokens()
        );

        Map<String, Object> response = client.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        return (String) content.get(0).get("text");
    }

    @SuppressWarnings("unchecked")
    private String completeViaOpenAiCompatible(CodegenLlmSelection selection, String systemPrompt, String userPrompt) {
        String baseUrl = nullIfBlank(selection.baseUrl());
        if (baseUrl == null) baseUrl = "https://api.openai.com";
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + selection.apiKey())
                .build();

        Map<String, Object> body = Map.of(
                "model", selection.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "max_tokens", selection.maxTokens()
        );

        Map<String, Object> response = client.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    private String withRetry(Supplier<String> call) {
        int attempt = 0;
        while (true) {
            try {
                return call.get();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() != 429 || ++attempt >= MAX_ATTEMPTS) throw e;
            } catch (HttpServerErrorException e) {
                if (++attempt >= MAX_ATTEMPTS) throw e;
            } catch (ResourceAccessException e) {
                if (++attempt >= MAX_ATTEMPTS) throw e;
            }
            try {
                Thread.sleep(BASE_DELAY_MS * (1L << attempt));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during LLM retry backoff", ie);
            }
        }
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
