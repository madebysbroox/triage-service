package com.example.triage.eval;

import com.example.triage.service.TriageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the golden set against the keyword-rule mock on every build. The mock is
 * the baseline any real model has to beat, and the review gates must hold even
 * for a provider this crude.
 */
@SpringBootTest
class MockProviderGoldenSetTest {
    // Pinned from the current mock. Raise it when the mock improves; a drop fails the build.
    static final double BASELINE_ACCURACY = 0.28;

    @Autowired TriageService triageService;
    @Autowired JsonMapper jsonMapper;

    @Test
    void mockMeetsBaselineAndNeverSkipsRequiredReview() throws Exception {
        EvalReport report = new GoldenSetEvaluator(triageService, jsonMapper).run();
        GoldenSetEvaluator.write(report, "mock");

        assertThat(report.total()).isGreaterThanOrEqualTo(30);
        assertThat(report.unsafeAutoApprovals()).as("unsafe auto-approvals").isZero();
        assertThat(report.classificationAccuracy()).as("classification accuracy").isGreaterThanOrEqualTo(BASELINE_ACCURACY);
    }
}
