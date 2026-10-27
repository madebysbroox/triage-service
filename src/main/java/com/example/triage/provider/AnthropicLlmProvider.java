package com.example.triage.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.MessageCreateParams;
import com.example.triage.config.TriageProperties;
import com.example.triage.model.FailureMetadata;
import com.example.triage.model.TriageResult;
import com.example.triage.service.LogSlice;
import com.example.triage.service.PromptBuilder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Calls Claude through the Messages API with structured outputs, so the response
 * is parsed straight into {@link TriageResult}. Validation and review gating still
 * happen in {@code TriageValidationService}: a well-formed answer is not a trusted one.
 */
@Component
@ConditionalOnProperty(prefix = "triage", name = "provider", havingValue = "anthropic")
public class AnthropicLlmProvider implements LlmProvider, DisposableBean {
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final PromptBuilder promptBuilder;
    private final TriageProperties.Anthropic settings;

    @Autowired
    public AnthropicLlmProvider(TriageProperties properties, PromptBuilder promptBuilder) {
        this(buildClient(properties.anthropic()), promptBuilder, properties.anthropic());
    }

    AnthropicLlmProvider(AnthropicClient client, PromptBuilder promptBuilder, TriageProperties.Anthropic settings) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.settings = settings;
    }

    private static AnthropicClient buildClient(TriageProperties.Anthropic settings) {
        if (settings == null || settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new IllegalStateException("triage.provider=anthropic requires ANTHROPIC_API_KEY");
        }
        return AnthropicOkHttpClient.builder()
                .apiKey(settings.apiKey())
                .baseUrl(settings.baseUrl())
                .build();
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public String model() {
        return settings.model();
    }

    @Override
    public ProviderResult complete(FailureMetadata metadata, LogSlice logSlice) {
        StructuredMessageCreateParams.Builder<TriageResult> params = MessageCreateParams.builder()
                .model(settings.model())
                .maxTokens(settings.maxTokens())
                .system(promptBuilder.systemPrompt())
                .addUserMessage(promptBuilder.userPrompt(metadata, logSlice))
                .outputConfig(StructuredOutputConfig.<TriageResult>builder()
                        .format(TriageResult.class)
                        .effort(OutputConfig.Effort.of(settings.effort().toLowerCase(Locale.ROOT)))
                        .build());
        if (settings.fallbacks()) {
            // If a safety classifier declines the request, let the API re-run it on
            // the recommended fallback model instead of returning a refusal.
            params.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }

        StructuredMessage<TriageResult> message = client.messages().create(params.build());

        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            String category = message.stopDetails().flatMap(d -> d.category()).map(Object::toString).orElse("unspecified");
            throw new ProviderRefusalException("Model declined the request (category: " + category + ")");
        }
        if (message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
            throw new IllegalStateException("Model output was cut off at max_tokens=" + settings.maxTokens());
        }

        TriageResult result = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Model returned no structured text block"));

        TokenUsage usage = TokenUsage.priced(
                message.usage().inputTokens(),
                message.usage().outputTokens(),
                settings.inputUsdPerMtok(),
                settings.outputUsdPerMtok());
        return new ProviderResult(name(), message.model().asString(), result, usage);
    }

    @Override
    public void destroy() {
        client.close();
    }
}
