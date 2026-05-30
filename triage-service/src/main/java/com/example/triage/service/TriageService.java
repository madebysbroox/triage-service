package com.example.triage.service;

import com.example.triage.model.TriageRequest;
import com.example.triage.model.TriageResponse;
import com.example.triage.provider.LlmProvider;
import com.example.triage.provider.ProviderResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class TriageService {
    private final RedactionService redactionService;
    private final LogSlicingService logSlicingService;
    private final LlmProvider provider;
    private final TriageValidationService validationService;
    private final AuditService auditService;

    public TriageService(RedactionService redactionService,
                         LogSlicingService logSlicingService,
                         LlmProvider provider,
                         TriageValidationService validationService,
                         AuditService auditService) {
        this.redactionService = redactionService;
        this.logSlicingService = logSlicingService;
        this.provider = provider;
        this.validationService = validationService;
        this.auditService = auditService;
    }

    public TriageResponse triage(TriageRequest request) {
        String requestId = UUID.randomUUID().toString();
        Instant start = Instant.now();
        RedactionResult redacted = redactionService.redact(request.log());
        LogSlice slice = logSlicingService.slice(redacted.text());

        ProviderResult providerResult;
        try {
            providerResult = provider.complete(request.metadata(), slice);
        } catch (Exception ex) {
            providerResult = new ProviderResult(provider.name(), provider.model(), validationService.fallback(ex.getMessage(), slice));
        }

        var gatedResult = validationService.validateAndGate(providerResult.triageResult(), slice);
        long latencyMs = Duration.between(start, Instant.now()).toMillis();
        TriageResponse response = new TriageResponse(
                requestId,
                providerResult.provider(),
                providerResult.model(),
                latencyMs,
                redacted.redactionsApplied(),
                slice.totalLines(),
                slice.usedLines(),
                gatedResult
        );
        auditService.emit(request.log(), response);
        return response;
    }
}
