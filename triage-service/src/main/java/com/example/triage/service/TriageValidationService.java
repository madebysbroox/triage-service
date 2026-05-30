package com.example.triage.service;

import com.example.triage.config.TriageProperties;
import com.example.triage.model.ErrorClassification;
import com.example.triage.model.Severity;
import com.example.triage.model.TriageResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class TriageValidationService {
    private final Validator validator;
    private final TriageProperties properties;

    public TriageValidationService(Validator validator, TriageProperties properties) {
        this.validator = validator;
        this.properties = properties;
    }

    public TriageResult validateAndGate(TriageResult result, LogSlice slice) {
        Set<ConstraintViolation<TriageResult>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            return fallback("Provider returned invalid triage output: " + violations.iterator().next().getMessage(), slice);
        }

        boolean evidenceMismatch = result.evidenceFromLog().stream().noneMatch(slice::containsEvidence);
        boolean forceReview = result.humanReviewRequired()
                || result.confidenceScore() < properties.gating().minConfidence()
                || result.errorClassification() == ErrorClassification.UNKNOWN
                || properties.gating().forceReviewSeverities().contains(result.severity().name())
                || evidenceMismatch;

        if (!forceReview) {
            return result;
        }

        List<String> reasons = new ArrayList<>();
        if (result.humanReviewRequired()) reasons.add("provider requested review");
        if (result.confidenceScore() < properties.gating().minConfidence()) reasons.add("confidence below threshold");
        if (result.errorClassification() == ErrorClassification.UNKNOWN) reasons.add("classification unknown");
        if (properties.gating().forceReviewSeverities().contains(result.severity().name())) reasons.add("severity requires review");
        if (evidenceMismatch) reasons.add("evidence could not be verified against sliced log");

        return new TriageResult(
                result.plainEnglishSummary(),
                result.likelyRootCause(),
                result.errorClassification(),
                result.likelyOwningTeam(),
                result.severity(),
                result.recommendedNextAction() + " Human review required: " + String.join(", ", reasons) + ".",
                result.confidenceScore(),
                result.evidenceFromLog(),
                true
        );
    }

    public TriageResult fallback(String reason, LogSlice slice) {
        return new TriageResult(
                "The failure could not be safely summarized by the provider.",
                reason,
                ErrorClassification.UNKNOWN,
                "Workflow Platform Team",
                Severity.MEDIUM,
                "Route to a human reviewer and inspect the full failed-workflow log.",
                0.0,
                slice.firstRelevantEvidence(),
                true
        );
    }
}
