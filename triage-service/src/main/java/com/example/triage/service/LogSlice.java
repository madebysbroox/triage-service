package com.example.triage.service;

import com.example.triage.model.LogEvidence;

import java.util.List;
import java.util.Locale;

public record LogSlice(List<LogLine> lines, int totalLines) {
    public String text() {
        StringBuilder builder = new StringBuilder();
        for (LogLine line : lines) {
            builder.append(line.number()).append(": ").append(line.text()).append(System.lineSeparator());
        }
        return builder.toString();
    }

    public int usedLines() {
        return lines.size();
    }

    public List<LogEvidence> firstRelevantEvidence() {
        for (LogLine line : lines) {
            String lower = line.text().toLowerCase(Locale.ROOT);
            if (lower.contains("error") || lower.contains("exception") || lower.contains("failed") || lower.contains("denied") || lower.contains("not found") || lower.contains("timeout")) {
                return List.of(new LogEvidence(line.number(), line.text()));
            }
        }
        if (!lines.isEmpty()) {
            LogLine first = lines.getFirst();
            return List.of(new LogEvidence(first.number(), first.text()));
        }
        return List.of(new LogEvidence(1, "No log lines supplied; classification is based on metadata only."));
    }

    public boolean containsEvidence(LogEvidence evidence) {
        return lines.stream().anyMatch(line -> line.number() == evidence.lineNumber() && line.text().contains(evidence.text()));
    }
}
