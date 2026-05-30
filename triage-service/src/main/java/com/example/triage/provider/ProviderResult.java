package com.example.triage.provider;

import com.example.triage.model.TriageResult;

public record ProviderResult(
        String provider,
        String model,
        TriageResult triageResult
) {}
