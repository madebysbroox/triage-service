package com.example.triage.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record FailureMetadata(
        @JsonAlias({"packageId", "failureRunId", "jobId"}) @Size(max = 128) String workflowId,
        FailureStage failureStage,
        @Size(max = 128) String pipeline,
        @Size(max = 128) String component,
        @Size(max = 64) String environment,
        OffsetDateTime timestamp
) {}
