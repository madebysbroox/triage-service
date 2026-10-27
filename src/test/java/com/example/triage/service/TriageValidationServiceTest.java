package com.example.triage.service;

import com.example.triage.model.ErrorClassification;
import com.example.triage.model.LogEvidence;
import com.example.triage.model.Severity;
import com.example.triage.model.TriageResult;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TriageValidationServiceTest {
    private static final LogLine ERROR_LINE = new LogLine(7, "ERROR Checksum mismatch for a.pdf");
    private static final LogSlice SLICE = new LogSlice(List.of(new LogLine(6, "INFO Verifying"), ERROR_LINE), 7);

    private final TriageValidationService service = new TriageValidationService(
            Validation.buildDefaultValidatorFactory().getValidator(), TestProperties.defaults());

    @Test
    void confidentVerifiedMediumResultPassesThrough() {
        TriageResult gated = service.validateAndGate(result(Severity.MEDIUM, 0.9, evidence(ERROR_LINE)), SLICE);
        assertThat(gated.humanReviewRequired()).isFalse();
    }

    @Test
    void lowConfidenceForcesReview() {
        TriageResult gated = service.validateAndGate(result(Severity.MEDIUM, 0.5, evidence(ERROR_LINE)), SLICE);
        assertThat(gated.humanReviewRequired()).isTrue();
        assertThat(gated.recommendedNextAction()).contains("confidence below threshold");
    }

    @Test
    void highSeverityForcesReview() {
        TriageResult gated = service.validateAndGate(result(Severity.HIGH, 0.99, evidence(ERROR_LINE)), SLICE);
        assertThat(gated.humanReviewRequired()).isTrue();
        assertThat(gated.recommendedNextAction()).contains("severity requires review");
    }

    @Test
    void oneInventedEvidenceLineForcesReview() {
        List<LogEvidence> evidence = List.of(
                new LogEvidence(7, "ERROR Checksum mismatch for a.pdf"),
                new LogEvidence(3, "ERROR a line the model made up"));
        TriageResult gated = service.validateAndGate(result(Severity.MEDIUM, 0.95, evidence), SLICE);
        assertThat(gated.humanReviewRequired()).isTrue();
        assertThat(gated.recommendedNextAction()).contains("evidence could not be verified");
    }

    @Test
    void evidenceWithWrongLineNumberForcesReview() {
        List<LogEvidence> evidence = List.of(new LogEvidence(6, "ERROR Checksum mismatch for a.pdf"));
        assertThat(service.validateAndGate(result(Severity.MEDIUM, 0.95, evidence), SLICE).humanReviewRequired()).isTrue();
    }

    @Test
    void schemaViolationFallsBackToUnknownWithReview() {
        TriageResult invalid = new TriageResult("", "cause", ErrorClassification.CHECKSUM_FAILURE, "team",
                Severity.MEDIUM, "act", 1.7, evidence(ERROR_LINE), false);
        TriageResult gated = service.validateAndGate(invalid, SLICE);
        assertThat(gated.errorClassification()).isEqualTo(ErrorClassification.UNKNOWN);
        assertThat(gated.humanReviewRequired()).isTrue();
        assertThat(gated.likelyRootCause()).startsWith("Provider returned invalid triage output");
    }

    private static List<LogEvidence> evidence(LogLine line) {
        return List.of(new LogEvidence(line.number(), line.text()));
    }

    private static TriageResult result(Severity severity, double confidence, List<LogEvidence> evidence) {
        return new TriageResult("summary", "cause", ErrorClassification.CHECKSUM_FAILURE, "Delivery Ops Team",
                severity, "Re-send the file.", confidence, evidence, false);
    }
}
