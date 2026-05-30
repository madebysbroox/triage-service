package com.example.triage.service;

import com.example.triage.model.TriageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditService {
    private static final Logger auditLogger = LoggerFactory.getLogger("TRIAGE_AUDIT");
    private final JsonMapper jsonMapper;

    public AuditService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void emit(String rawLog, TriageResponse response) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("event", "triage_completed");
        audit.put("requestId", response.requestId());
        audit.put("provider", response.provider());
        audit.put("model", response.model());
        audit.put("latencyMs", response.latencyMs());
        audit.put("redactionsApplied", response.redactionsApplied());
        audit.put("logLinesIn", response.logLinesIn());
        audit.put("logLinesUsed", response.logLinesUsed());
        audit.put("rawInputSha256", sha256(rawLog == null ? "" : rawLog));
        audit.put("classification", response.triage().errorClassification().name());
        audit.put("severity", response.triage().severity().name());
        audit.put("confidence", response.triage().confidenceScore());
        audit.put("humanReviewRequired", response.triage().humanReviewRequired());
        try {
            auditLogger.info(jsonMapper.writeValueAsString(audit));
        } catch (JacksonException e) {
            auditLogger.warn("Unable to serialize triage audit event", e);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
