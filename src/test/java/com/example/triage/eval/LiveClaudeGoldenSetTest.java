package com.example.triage.eval;

import com.example.triage.service.TriageService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same golden set against real Claude. It costs money, so it only runs with
 * {@code mvn verify -Plive-eval} and an ANTHROPIC_API_KEY in the environment.
 * The report lands in target/eval/claude.md.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@SpringBootTest(properties = "triage.provider=anthropic")
class LiveClaudeGoldenSetTest {
    static final double MIN_ACCURACY = 0.80;

    @Autowired TriageService triageService;
    @Autowired JsonMapper jsonMapper;

    @Test
    void claudeBeatsBaselineAndNeverSkipsRequiredReview() throws Exception {
        EvalReport report = new GoldenSetEvaluator(triageService, jsonMapper).run();
        GoldenSetEvaluator.write(report, "claude");

        assertThat(report.unsafeAutoApprovals()).as("unsafe auto-approvals").isZero();
        assertThat(report.classificationAccuracy()).as("classification accuracy").isGreaterThanOrEqualTo(MIN_ACCURACY);
    }
}
