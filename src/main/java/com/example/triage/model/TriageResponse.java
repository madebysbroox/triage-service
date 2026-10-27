package com.example.triage.model;

import com.example.triage.provider.TokenUsage;

public record TriageResponse(
        String requestId,
        String provider,
        String model,
        long latencyMs,
        long providerLatencyMs,
        int redactionsApplied,
        int logLinesIn,
        int logLinesUsed,
        TokenUsage usage,
        TriageResult triage
) {}
