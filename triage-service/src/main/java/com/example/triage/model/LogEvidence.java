package com.example.triage.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LogEvidence(
        @Min(1) int lineNumber,
        @NotBlank String text
) {}
