package com.crablet.codegen.llm;

import com.embabel.agent.config.models.anthropic.AnthropicModelFactory;
import com.embabel.agent.openai.OpenAiCompatibleModelFactory;
import com.embabel.agent.spi.LlmService;
import com.embabel.chat.SystemMessage;
import com.embabel.chat.UserMessage;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.PricingModel;
import com.embabel.common.util.ObjectProviders;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
class EmbabelCodegenLlmClient implements CodegenLlmClient {

    private final CodegenLlmProperties properties;

    EmbabelCodegenLlmClient(CodegenLlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public @NonNull String complete(@NonNull String systemPrompt, @NonNull String userPrompt) {
        CodegenLlmSelection selection = properties.selection();
        LlmService<?> service = llmService(selection);
        LlmOptions options = LlmOptions.withDefaults().withMaxTokens(selection.maxTokens());
        return Objects.requireNonNull(
                service.createMessageSender(options)
                        .call(
                                List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)),
                                List.of()
                        )
                        .getTextContent());
    }

    private LlmService<?> llmService(CodegenLlmSelection selection) {
        if ("anthropic".equals(selection.provider())) {
            return new AnthropicModelFactory(
                    selection.apiKey(),
                    nullIfBlank(selection.baseUrl()),
                    null,
                    ObservationRegistry.NOOP,
                    ObjectProviders.INSTANCE.empty()
            ).build(selection.model(), RetryUtils.DEFAULT_RETRY_TEMPLATE);
        }

        OpenAiCompatibleModelFactory factory = new OpenAiCompatibleModelFactory(
                nullIfBlank(selection.baseUrl()),
                selection.apiKey(),
                null,
                null,
                Map.of(),
                ObservationRegistry.NOOP,
                ObjectProviders.INSTANCE.empty()
        );
        return factory.openAiCompatibleLlm(
                selection.model(),
                PricingModel.getALL_YOU_CAN_EAT(),
                selection.provider(),
                null
        );
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
