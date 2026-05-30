package com.example.triage.provider;

import com.example.triage.config.TriageProperties;
import com.example.triage.model.*;
import com.example.triage.service.LogSlice;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "triage", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmProvider implements LlmProvider {
    private final TriageProperties properties;

    public MockLlmProvider(TriageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String model() {
        return properties.model().id();
    }

    @Override
    public ProviderResult complete(FailureMetadata metadata, LogSlice logSlice) {
        String text = logSlice.text().toLowerCase(Locale.ROOT);
        TriageResult result;
        if (containsAny(text, "schema validation", "xsd", "required field", "missing required")) {
            result = build(
                    "The workflow failed validation because required structured metadata was missing or invalid.",
                    "A schema or required-field validation error is present in the workflow metadata or payload.",
                    ErrorClassification.SCHEMA_VALIDATION,
                    owner(metadata, "Content Validation Team"),
                    Severity.MEDIUM,
                    "Review the failing metadata field, correct the source payload or workflow input, and rerun validation.",
                    0.86,
                    logSlice.firstRelevantEvidence()
            );
        } else if (containsAny(text, "no such key", "s3 object", "missing asset", "file not found", "not found")) {
            result = build(
                    "The workflow references an asset that could not be found during processing.",
                    "A referenced media or content asset is missing from the expected storage location.",
                    ErrorClassification.MISSING_ASSET,
                    owner(metadata, "Media Ingestion Team"),
                    Severity.MEDIUM,
                    "Verify that all referenced assets exist in the expected storage location, then retry.",
                    0.84,
                    logSlice.firstRelevantEvidence()
            );
        } else if (containsAny(text, "timeout", "timed out", "connection reset", "503", "502")) {
            result = build(
                    "The workflow failed because a dependency or network call did not complete successfully.",
                    "A downstream dependency, network connection, or service endpoint appears unavailable or slow.",
                    ErrorClassification.DEPENDENCY_FAILURE,
                    owner(metadata, "Platform Operations Team"),
                    Severity.HIGH,
                    "Check dependency health and retry after confirming the downstream service has recovered.",
                    0.78,
                    logSlice.firstRelevantEvidence()
            );
        } else if (containsAny(text, "unauthorized", "forbidden", "access denied", "permission")) {
            result = build(
                    "The workflow failed because a service call or storage operation was not authorized.",
                    "Credentials, IAM permissions, or service authorization appear misconfigured.",
                    ErrorClassification.AUTH,
                    owner(metadata, "Platform Security Team"),
                    Severity.HIGH,
                    "Verify the service role, credential scope, and access policy for the failing component.",
                    0.80,
                    logSlice.firstRelevantEvidence()
            );
        } else {
            result = build(
                    "The failure could not be confidently classified from the available log context.",
                    "The sliced log did not contain a clear known failure signature.",
                    ErrorClassification.UNKNOWN,
                    owner(metadata, "Workflow Platform Team"),
                    Severity.MEDIUM,
                    "Have an engineer review the full failed workflow logs and add a golden-set example if this pattern recurs.",
                    0.35,
                    logSlice.firstRelevantEvidence()
            );
        }
        return new ProviderResult(name(), model(), result);
    }

    private TriageResult build(String summary, String rootCause, ErrorClassification classification, String owner,
                               Severity severity, String action, double confidence, List<LogEvidence> evidence) {
        return new TriageResult(summary, rootCause, classification, owner, severity, action, confidence, evidence, false);
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String owner(FailureMetadata metadata, String fallback) {
        if (metadata == null || metadata.component() == null) {
            return fallback;
        }
        return switch (metadata.component()) {
            case "validation-worker" -> "Content Validation Team";
            case "manifest-generator" -> "Delivery Packaging Team";
            case "s3-media-loader" -> "Media Ingestion Team";
            case "marklogic-writer" -> "Content Platform Team";
            case "ftp-delivery-adapter" -> "Delivery Ops Team";
            default -> fallback;
        };
    }
}
