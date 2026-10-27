package com.example.triage.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.example.triage.config.TriageProperties;
import com.example.triage.model.ErrorClassification;
import com.example.triage.model.Severity;
import com.example.triage.service.LogLine;
import com.example.triage.service.LogSlice;
import com.example.triage.service.PromptBuilder;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Drives the real SDK client against a local stub of the Messages API. */
class AnthropicLlmProviderTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TriageProperties.Anthropic SETTINGS = new TriageProperties.Anthropic(
            "test-key", null, "claude-opus-5-5", 4096, "low", true, 4.00, 20.00);
    private static final LogSlice SLICE = new LogSlice(
            List.of(new LogLine(4, "2026-05-27T03:14:07Z ERROR Schema validation failed: field rightsWindow.end is null")), 5);

    private HttpServer server;
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> betaHeader = new AtomicReference<>();
    private AnthropicLlmProvider provider;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            betaHeader.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .maxRetries(0)
                .build();
        provider = new AnthropicLlmProvider(client, new PromptBuilder(JSON), SETTINGS);
    }

    @AfterEach
    void stop() {
        provider.destroy();
        server.stop(0);
    }

    @Test
    void parsesStructuredResultAndPricesUsage() {
        responseBody.set(message("end_turn", """
                {"plainEnglishSummary":"Rights metadata is incomplete.",
                 "likelyRootCause":"rightsWindow.end is null in the source payload.",
                 "errorClassification":"SCHEMA_VALIDATION",
                 "likelyOwningTeam":"Content Validation Team",
                 "severity":"MEDIUM",
                 "recommendedNextAction":"Fix the rights window in the source and rerun validation.",
                 "confidenceScore":0.92,
                 "evidenceFromLog":[{"lineNumber":4,"text":"ERROR Schema validation failed: field rightsWindow.end is null"}],
                 "humanReviewRequired":false}"""));

        ProviderResult result = provider.complete(null, SLICE);

        assertThat(result.provider()).isEqualTo("anthropic");
        assertThat(result.triageResult().errorClassification()).isEqualTo(ErrorClassification.SCHEMA_VALIDATION);
        assertThat(result.triageResult().severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.triageResult().evidenceFromLog()).hasSize(1);
        assertThat(result.usage().inputTokens()).isEqualTo(1200);
        assertThat(result.usage().outputTokens()).isEqualTo(150);
        // 1200 * $4/M + 150 * $20/M
        assertThat(result.usage().estimatedCostUsd()).isCloseTo(0.0078, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void sendsSchemaEffortAndFallbacks() {
        responseBody.set(message("end_turn", "{\"plainEnglishSummary\":\"x\",\"likelyRootCause\":\"x\",\"errorClassification\":\"UNKNOWN\",\"likelyOwningTeam\":\"x\",\"severity\":\"LOW\",\"recommendedNextAction\":\"x\",\"confidenceScore\":0.1,\"evidenceFromLog\":[],\"humanReviewRequired\":true}"));

        provider.complete(null, SLICE);

        JsonNode sent = JSON.readTree(requestBody.get());
        assertThat(sent.path("model").asString()).isEqualTo("claude-opus-5-5");
        assertThat(sent.path("output_config").path("effort").asString()).isEqualTo("low");
        assertThat(sent.path("output_config").path("format").path("type").asString()).isEqualTo("json_schema");
        assertThat(sent.path("fallbacks").asString()).isEqualTo("default");
        assertThat(betaHeader.get()).contains(AnthropicLlmProvider.FALLBACK_BETA);
        assertThat(sent.path("system").toString()).contains("untrusted diagnostic data");
    }

    @Test
    void refusalBecomesProviderRefusal() {
        responseBody.set("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],
                 "stop_reason":"refusal","stop_sequence":null,
                 "stop_details":{"type":"refusal","category":"cyber","explanation":null},
                 "usage":{"input_tokens":900,"output_tokens":0}}""");

        assertThatThrownBy(() -> provider.complete(null, SLICE))
                .isInstanceOf(ProviderRefusalException.class)
                .hasMessageContaining("cyber");
    }

    @Test
    void truncatedOutputIsAnError() {
        responseBody.set(message("max_tokens", "{\"plainEnglishSummary\":\"cut"));

        assertThatThrownBy(() -> provider.complete(null, SLICE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max_tokens");
    }

    private static String message(String stopReason, String text) {
        String escaped = JSON.writeValueAsString(text);
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[{"type":"text","text":%s}],
                 "stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":1200,"output_tokens":150}}""".formatted(escaped, stopReason);
    }
}
