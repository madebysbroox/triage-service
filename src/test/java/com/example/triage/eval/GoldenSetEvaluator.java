package com.example.triage.eval;

import com.example.triage.model.TriageRequest;
import com.example.triage.model.TriageResponse;
import com.example.triage.service.TriageService;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs every golden case through the full pipeline (redaction, slicing, provider,
 * validation, gating) and scores the gated result, which is what callers see.
 */
public class GoldenSetEvaluator {
    public static final String GOLDEN_SET = "/golden/golden-set.jsonl";

    private final TriageService triageService;
    private final JsonMapper jsonMapper;

    public GoldenSetEvaluator(TriageService triageService, JsonMapper jsonMapper) {
        this.triageService = triageService;
        this.jsonMapper = jsonMapper;
    }

    public List<GoldenCase> loadCases() throws IOException {
        List<GoldenCase> cases = new ArrayList<>();
        try (InputStream in = GoldenSetEvaluator.class.getResourceAsStream(GOLDEN_SET);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    cases.add(jsonMapper.readValue(line, GoldenCase.class));
                }
            }
        }
        return cases;
    }

    public EvalReport run() throws IOException {
        List<EvalReport.Outcome> outcomes = new ArrayList<>();
        for (GoldenCase goldenCase : loadCases()) {
            TriageResponse response = triageService.triage(new TriageRequest(goldenCase.log(), goldenCase.metadata()));
            outcomes.add(new EvalReport.Outcome(goldenCase, response));
        }
        return new EvalReport(outcomes);
    }

    public static Path write(EvalReport report, String name) throws IOException {
        Path dir = Path.of("target", "eval");
        Files.createDirectories(dir);
        Path markdown = dir.resolve(name.toLowerCase(Locale.ROOT) + ".md");
        Files.writeString(markdown, report.toMarkdown(name));
        return markdown;
    }
}
