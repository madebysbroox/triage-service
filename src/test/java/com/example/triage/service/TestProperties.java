package com.example.triage.service;

import com.example.triage.config.TriageProperties;

import java.util.List;

final class TestProperties {
    private TestProperties() {}

    static TriageProperties defaults() {
        return new TriageProperties(
                "mock", "test-key", 1_048_576,
                new TriageProperties.LogSlicing(120, 3, 6),
                new TriageProperties.Gating(0.75, List.of("HIGH", "CRITICAL")),
                new TriageProperties.Model("mock-deterministic-v1"),
                null);
    }

    static TriageProperties withMaxLines(int maxLines) {
        TriageProperties d = defaults();
        return new TriageProperties(d.provider(), d.apiKey(), d.maxBodyBytes(),
                new TriageProperties.LogSlicing(maxLines, 2, 2), d.gating(), d.model(), d.anthropic());
    }
}
