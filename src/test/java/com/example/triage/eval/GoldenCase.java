package com.example.triage.eval;

import com.example.triage.model.ErrorClassification;
import com.example.triage.model.FailureMetadata;
import com.example.triage.model.Severity;

/** One labelled failure from src/test/resources/golden/golden-set.jsonl. */
public record GoldenCase(
        String id,
        String description,
        String log,
        FailureMetadata metadata,
        Expected expected
) {
    /**
     * mustReview marks cases a human has to see. Letting one of these through
     * without review is a safety failure, not an accuracy miss.
     */
    public record Expected(ErrorClassification classification, Severity severity, boolean mustReview) {}
}
