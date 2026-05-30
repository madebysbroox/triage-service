package com.example.triage.service;

import com.example.triage.config.TriageProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LogSlicingService {
    private final TriageProperties properties;

    public LogSlicingService(TriageProperties properties) {
        this.properties = properties;
    }

    public LogSlice slice(String redactedLog) {
        if (redactedLog == null || redactedLog.isBlank()) {
            return new LogSlice(List.of(), 0);
        }
        String[] rawLines = redactedLog.split("\\R", -1);
        List<LogLine> numbered = new ArrayList<>();
        for (int i = 0; i < rawLines.length; i++) {
            if (!rawLines[i].isBlank()) {
                numbered.add(new LogLine(i + 1, rawLines[i]));
            }
        }
        if (numbered.size() <= properties.logSlicing().maxLines()) {
            return new LogSlice(numbered, numbered.size());
        }

        Map<Integer, LogLine> selected = new LinkedHashMap<>();
        for (int i = 0; i < numbered.size(); i++) {
            if (isRelevant(numbered.get(i).text())) {
                int from = Math.max(0, i - properties.logSlicing().contextBefore());
                int to = Math.min(numbered.size() - 1, i + properties.logSlicing().contextAfter());
                for (int j = from; j <= to; j++) {
                    selected.put(numbered.get(j).number(), numbered.get(j));
                    if (selected.size() >= properties.logSlicing().maxLines()) {
                        return new LogSlice(new ArrayList<>(selected.values()), numbered.size());
                    }
                }
            }
        }

        if (selected.isEmpty()) {
            for (int i = Math.max(0, numbered.size() - properties.logSlicing().maxLines()); i < numbered.size(); i++) {
                selected.put(numbered.get(i).number(), numbered.get(i));
            }
        }
        return new LogSlice(new ArrayList<>(selected.values()), numbered.size());
    }

    private boolean isRelevant(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("error") || lower.contains("exception") || lower.contains("failed") || lower.contains("fatal")
                || lower.contains("timeout") || lower.contains("denied") || lower.contains("not found") || lower.contains("missing");
    }
}
