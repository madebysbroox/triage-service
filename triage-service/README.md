# Triage Service — Spring Boot POC

A narrow, read-only Spring Boot POC for AI-enriched classification and summarization of failed operational workflows across ingestion, packaging, validation, delivery, and other backend services.

The service accepts a failed workflow log and/or failure metadata, redacts sensitive values, slices the log down to relevant evidence windows, calls a provider-agnostic `LlmProvider`, validates the structured triage result, applies deterministic human-review gates, and emits an audit log.

## Why this version

This is the Java/Spring Boot version of the earlier Python vertical slice. It is intended to fit better inside a Java service ecosystem while preserving the same safety boundaries:

- read-only API
- no autonomous fixes
- provider-agnostic LLM interface
- redaction before provider calls or audit logging
- line-numbered evidence
- deterministic mock provider for offline development
- confidence/severity/classification gates outside the model
- structured audit events without raw logs

## API

### `POST /v1/triage`

Header:

```http
x-api-key: local-dev-key
content-type: application/json
```

Request:

```json
{
  "log": "2026-05-27T03:14:07Z ERROR Schema validation failed: field rightsWindow.end is null",
  "metadata": {
    "workflowId": "WF-2026-0099831",
    "failureStage": "VALIDATION",
    "pipeline": "content-prod",
    "component": "validation-worker",
    "environment": "prod",
    "timestamp": "2026-05-27T03:14:07Z"
  }
}
```

Response:

```json
{
  "requestId": "...",
  "provider": "mock",
  "model": "mock-deterministic-v1",
  "latencyMs": 12,
  "redactionsApplied": 0,
  "logLinesIn": 1,
  "logLinesUsed": 1,
  "triage": {
    "plainEnglishSummary": "...",
    "likelyRootCause": "...",
    "errorClassification": "SCHEMA_VALIDATION",
    "likelyOwningTeam": "Content Validation Team",
    "severity": "MEDIUM",
    "recommendedNextAction": "...",
    "confidenceScore": 0.86,
    "evidenceFromLog": [
      { "lineNumber": 1, "text": "2026-05-27T03:14:07Z ERROR Schema validation failed: field rightsWindow.end is null" }
    ],
    "humanReviewRequired": false
  }
}
```

### `GET /health`

Returns active provider and model.

## Run locally

Requires Java 21 and Maven.

```bash
mvn test
mvn spring-boot:run
```

Then call:

```bash
curl -s -X POST http://localhost:8080/v1/triage \
  -H 'x-api-key: local-dev-key' \
  -H 'content-type: application/json' \
  -d @- <<'JSON'
{
  "log": "2026-05-27T03:14:07Z ERROR Schema validation failed: field rightsWindow.end is null",
  "metadata": {
    "workflowId": "WF-2026-0099831",
    "failureStage": "VALIDATION",
    "pipeline": "content-prod",
    "component": "validation-worker",
    "environment": "prod",
    "timestamp": "2026-05-27T03:14:07Z"
  }
}
JSON
```

## Project layout

```text
src/main/java/com/example/triage
  config/       API key, request-size, properties
  controller/   /v1/triage, /health, error handling
  model/        request/response DTOs and controlled enums
  provider/     LlmProvider interface, mock provider, Bedrock stub
  service/      redaction, slicing, validation/gating, audit, orchestration
```

## Provider strategy

`LlmProvider` is the only abstraction the pipeline depends on:

```java
public interface LlmProvider {
    String name();
    String model();
    ProviderResult complete(FailureMetadata metadata, LogSlice logSlice);
}
```

The included `MockLlmProvider` is deterministic and intentionally simple. It classifies common operational failure patterns using keywords so the whole system runs offline.

`BedrockLlmProvider` is a stub for Slice 1. Wire AWS SDK `BedrockRuntimeClient` there, serialize the prompt using `PromptBuilder`, parse the returned JSON into `TriageResult`, and let `TriageValidationService` enforce the final gates.

## Safety features included

- API key check for `/v1/triage`
- 1 MB request body limit
- secret/PII redaction before provider use
- log slicing around likely failure lines
- evidence line numbers
- Jakarta Bean Validation on inbound request and provider triage result
- deterministic post-provider review gates
- audit log stores SHA-256 of raw input, not raw log text

## Recommended next additions

1. Add a `PromptBuilder` class when wiring a real LLM provider.
2. Add golden-set tests with 20–50 historical failed workflow logs.
3. Add ownership mapping from config or database instead of a switch statement.
4. Emit Micrometer counters for classification, severity, review rate, and provider latency.
5. Add support for read-only log lookup by `workflowId` / `failureRunId`.

`FailureMetadata.workflowId` also accepts JSON aliases `packageId`, `failureRunId`, and `jobId` for compatibility with different caller domains.
