package com.example.triage.model;

public record TriageResponse(
        String requestId,
        String provider,
        String model,
        long latencyMs,
        int redactionsApplied,
        int logLinesIn,
        int logLinesUsed,
        TriageResult triage
) {}
