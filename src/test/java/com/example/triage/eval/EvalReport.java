package com.example.triage.eval;

import com.example.triage.model.TriageResponse;
import com.example.triage.model.TriageResult;

import java.util.List;
import java.util.Locale;

public record EvalReport(List<Outcome> outcomes) {
    public record Outcome(GoldenCase goldenCase, TriageResponse response) {
        TriageResult result() {
            return response.triage();
        }

        boolean classificationCorrect() {
            return result().errorClassification() == goldenCase.expected().classification();
        }

        boolean severityCorrect() {
            return result().severity() == goldenCase.expected().severity();
        }

        /** A case a human had to see went out without review. */
        boolean unsafeAutoApproval() {
            return goldenCase.expected().mustReview() && !result().humanReviewRequired();
        }

        /** Sent to a human although the answer was right and review was not required. */
        boolean needlessReview() {
            return !goldenCase.expected().mustReview() && result().humanReviewRequired() && classificationCorrect();
        }
    }

    public int total() {
        return outcomes.size();
    }

    public double classificationAccuracy() {
        return fraction(outcomes.stream().filter(Outcome::classificationCorrect).count());
    }

    public double severityAccuracy() {
        return fraction(outcomes.stream().filter(Outcome::severityCorrect).count());
    }

    public long unsafeAutoApprovals() {
        return outcomes.stream().filter(Outcome::unsafeAutoApproval).count();
    }

    public double reviewRate() {
        return fraction(outcomes.stream().filter(o -> o.result().humanReviewRequired()).count());
    }

    public long needlessReviews() {
        return outcomes.stream().filter(Outcome::needlessReview).count();
    }

    public double totalCostUsd() {
        return outcomes.stream().mapToDouble(o -> o.response().usage().estimatedCostUsd()).sum();
    }

    public long p50ProviderLatencyMs() {
        return percentile(0.50);
    }

    public long p99ProviderLatencyMs() {
        return percentile(0.99);
    }

    private long percentile(double p) {
        long[] sorted = outcomes.stream().mapToLong(o -> o.response().providerLatencyMs()).sorted().toArray();
        if (sorted.length == 0) return 0;
        int index = (int) Math.ceil(p * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private double fraction(long count) {
        return total() == 0 ? 0 : (double) count / total();
    }

    public String toMarkdown(String name) {
        StringBuilder md = new StringBuilder();
        String model = outcomes.isEmpty() ? "n/a" : outcomes.getFirst().response().model();
        md.append("# Golden-set eval: ").append(name).append("\n\n");
        md.append("Model `").append(model).append("`, ").append(total()).append(" cases.\n\n");
        md.append("| Metric | Value |\n| --- | --- |\n");
        md.append("| Classification accuracy | ").append(pct(classificationAccuracy())).append(" |\n");
        md.append("| Severity accuracy | ").append(pct(severityAccuracy())).append(" |\n");
        md.append("| Unsafe auto-approvals (must be 0) | ").append(unsafeAutoApprovals()).append(" |\n");
        md.append("| Review rate | ").append(pct(reviewRate())).append(" |\n");
        md.append("| Needless reviews | ").append(needlessReviews()).append(" |\n");
        md.append("| Provider latency p50 / p99 | ").append(p50ProviderLatencyMs()).append(" ms / ")
                .append(p99ProviderLatencyMs()).append(" ms |\n");
        md.append("| Estimated cost, whole set | $").append(String.format(Locale.ROOT, "%.4f", totalCostUsd())).append(" |\n\n");
        md.append("| Case | Expected | Got | Severity (exp / got) | Review (must / got) | Confidence |\n");
        md.append("| --- | --- | --- | --- | --- | --- |\n");
        for (Outcome o : outcomes) {
            var exp = o.goldenCase().expected();
            md.append("| ").append(o.unsafeAutoApproval() ? "**UNSAFE** " : "").append(o.goldenCase().id())
                    .append(" | ").append(exp.classification())
                    .append(" | ").append(o.classificationCorrect() ? "" : "✗ ").append(o.result().errorClassification())
                    .append(" | ").append(exp.severity()).append(" / ").append(o.result().severity())
                    .append(" | ").append(exp.mustReview()).append(" / ").append(o.result().humanReviewRequired())
                    .append(" | ").append(String.format(Locale.ROOT, "%.2f", o.result().confidenceScore()))
                    .append(" |\n");
        }
        return md.toString();
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }
}
