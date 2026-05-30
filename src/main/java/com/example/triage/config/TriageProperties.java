package com.example.triage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "triage")
public record TriageProperties(
        String provider,
        String apiKey,
        int maxBodyBytes,
        LogSlicing logSlicing,
        Gating gating,
        Model model
) {
    public record LogSlicing(int maxLines, int contextBefore, int contextAfter) {}
    public record Gating(double minConfidence, List<String> forceReviewSeverities) {}
    public record Model(String id) {}
}
