package com.example.triage.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public record TriageRequest(
        @Size(max = 1_000_000) String log,
        @Valid FailureMetadata metadata
) {
    @AssertTrue(message = "At least one of log or metadata is required")
    public boolean hasLogOrMetadata() {
        return (log != null && !log.isBlank()) || metadata != null;
    }
}
