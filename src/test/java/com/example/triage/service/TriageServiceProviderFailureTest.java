package com.example.triage.service;

import com.example.triage.model.ErrorClassification;
import com.example.triage.model.FailureMetadata;
import com.example.triage.model.TriageRequest;
import com.example.triage.model.TriageResponse;
import com.example.triage.provider.LlmProvider;
import com.example.triage.provider.ProviderRefusalException;
import com.example.triage.provider.ProviderResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TriageServiceProviderFailureTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void refusalBecomesUnknownForReviewAndIsCounted() {
        TriageResponse response = service(new LlmProvider() {
            public String name() { return "anthropic"; }
            public String model() { return "claude-opus-5-5"; }
            public ProviderResult complete(FailureMetadata metadata, LogSlice logSlice) {
                throw new ProviderRefusalException("Model declined the request (category: cyber)");
            }
        }).triage(new TriageRequest("ERROR something broke", null));

        assertThat(response.triage().errorClassification()).isEqualTo(ErrorClassification.UNKNOWN);
        assertThat(response.triage().humanReviewRequired()).isTrue();
        assertThat(response.triage().likelyRootCause()).contains("declined");
        assertThat(registry.counter("triage.provider.failures", "provider", "anthropic").count()).isEqualTo(1.0);
    }

    private TriageService service(LlmProvider provider) {
        var properties = TestProperties.defaults();
        return new TriageService(
                new RedactionService(),
                new LogSlicingService(properties),
                provider,
                new TriageValidationService(Validation.buildDefaultValidatorFactory().getValidator(), properties),
                new AuditService(JsonMapper.builder().build()),
                new TriageMetrics(registry));
    }
}
