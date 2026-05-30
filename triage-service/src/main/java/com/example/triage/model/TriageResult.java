package com.example.triage.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TriageResult(
        @NotBlank String plainEnglishSummary,
        @NotBlank String likelyRootCause,
        @NotNull ErrorClassification errorClassification,
        @NotBlank String likelyOwningTeam,
        @NotNull Severity severity,
        @NotBlank String recommendedNextAction,
        @DecimalMin("0.0") @DecimalMax("1.0") double confidenceScore,
        @NotEmpty @Valid List<LogEvidence> evidenceFromLog,
        boolean humanReviewRequired
) {}
