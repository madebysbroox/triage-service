package com.example.triage.provider;

/**
 * Tokens billed for one provider call and what they cost at the configured rates.
 * The mock provider reports zeros.
 */
public record TokenUsage(
        long inputTokens,
        long outputTokens,
        double estimatedCostUsd
) {
    public static final TokenUsage NONE = new TokenUsage(0, 0, 0.0);

    public static TokenUsage priced(long inputTokens, long outputTokens, double inputUsdPerMtok, double outputUsdPerMtok) {
        double cost = (inputTokens * inputUsdPerMtok + outputTokens * outputUsdPerMtok) / 1_000_000.0;
        return new TokenUsage(inputTokens, outputTokens, cost);
    }
}
