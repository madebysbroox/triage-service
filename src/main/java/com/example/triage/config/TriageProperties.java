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
        Model model,
        Anthropic anthropic
) {
    public record LogSlicing(int maxLines, int contextBefore, int contextAfter) {}
    public record Gating(double minConfidence, List<String> forceReviewSeverities) {}
    public record Model(String id) {}

    /**
     * Settings for the Claude provider. Prices are USD per million tokens and only
     * feed the cost estimate returned with each triage; they do not affect billing.
     */
    public record Anthropic(
            String apiKey,
            String baseUrl,
            String model,
            long maxTokens,
            String effort,
            boolean fallbacks,
            double inputUsdPerMtok,
            double outputUsdPerMtok
    ) {}
}
