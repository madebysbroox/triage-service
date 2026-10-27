package com.example.triage.service;

import com.example.triage.model.TriageRequest;
import com.example.triage.model.TriageResponse;
import com.example.triage.provider.LlmProvider;
import com.example.triage.provider.ProviderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class TriageService {
    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private final RedactionService redactionService;
    private final LogSlicingService logSlicingService;
    private final LlmProvider provider;
    private final TriageValidationService validationService;
    private final AuditService auditService;
    private final TriageMetrics metrics;

    public TriageService(RedactionService redactionService,
                         LogSlicingService logSlicingService,
                         LlmProvider provider,
                         TriageValidationService validationService,
                         AuditService auditService,
                         TriageMetrics metrics) {
        this.redactionService = redactionService;
        this.logSlicingService = logSlicingService;
        this.provider = provider;
        this.validationService = validationService;
        this.auditService = auditService;
        this.metrics = metrics;
    }

    public TriageResponse triage(TriageRequest request) {
        String requestId = UUID.randomUUID().toString();
        Instant start = Instant.now();
        RedactionResult redacted = redactionService.redact(request.log());
        LogSlice slice = logSlicingService.slice(redacted.text());

        ProviderResult providerResult;
        boolean providerFailed = false;
        Instant providerStart = Instant.now();
        try {
            providerResult = provider.complete(request.metadata(), slice);
        } catch (Exception ex) {
            providerFailed = true;
            log.warn("Provider {} failed for request {}: {}", provider.name(), requestId, ex.toString());
            providerResult = new ProviderResult(provider.name(), provider.model(), validationService.fallback(ex.getMessage(), slice));
        }
        long providerLatencyMs = Duration.between(providerStart, Instant.now()).toMillis();

        var gatedResult = validationService.validateAndGate(providerResult.triageResult(), slice);
        long latencyMs = Duration.between(start, Instant.now()).toMillis();
        TriageResponse response = new TriageResponse(
                requestId,
                providerResult.provider(),
                providerResult.model(),
                latencyMs,
                providerLatencyMs,
                redacted.redactionsApplied(),
                slice.totalLines(),
                slice.usedLines(),
                providerResult.usage(),
                gatedResult
        );
        auditService.emit(request.log(), response);
        metrics.record(response, providerFailed);
        return response;
    }
}
