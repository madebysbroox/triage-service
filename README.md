# Triage Service

[![ci](https://github.com/madebysbroox/triage-service/actions/workflows/ci.yml/badge.svg)](https://github.com/madebysbroox/triage-service/actions/workflows/ci.yml)

A read-only Spring Boot service for AI-enriched classification and summarization of failed operational workflows across ingestion, packaging, validation, delivery, and other backend services.

The service accepts a failed workflow log and/or failure metadata, redacts sensitive values, slices the log down to relevant evidence windows, calls a provider-agnostic `LlmProvider`, validates the structured triage result, applies deterministic human-review gates, and emits an audit log.

The model never gets the last word. Its answer is parsed against a schema, every line it quotes as evidence is checked against the log it was shown, and anything uncertain, severe, unverifiable or refused goes to a human. A labelled golden set measures both how often the answer is right and whether a case that needed a human ever slipped through.

## How a request flows

1. **Redact** secrets, tokens, keys, emails, IPs and connection strings before anything leaves the service.
2. **Slice** long logs down to windows around error lines, keeping original line numbers.
3. **Ask the provider.** `anthropic` calls Claude with structured outputs, so the reply is parsed straight into `TriageResult`. `mock` is a deterministic keyword baseline for local runs and CI.
4. **Validate and gate.** Schema violations fall back to `UNKNOWN`. Review is forced when confidence is below the threshold, severity is HIGH or CRITICAL, the class is UNKNOWN, or any evidence line does not match the slice.
5. **Audit and measure.** One JSON audit event per request (raw input stored only as a SHA-256 hash) and Micrometer metrics for classification, review rate, provider latency, tokens, cost and failures.

A provider error, timeout or refusal never fails the request: it becomes an `UNKNOWN` result that requires review, and `triage.provider.failures` is incremented.

## Current Shape

This project already follows the standard Spring Boot layout:

```text
src/main/java/com/example/triage
  TriageServiceApplication.java
  config/       API key, request size, bound configuration
  controller/   REST controllers and exception handling
  model/        request/response DTOs and enums
  provider/     provider abstraction, Claude provider, mock baseline, Bedrock stub
  service/      redaction, log slicing, validation/gating, audit, metrics, orchestration

src/test/java/com/example/triage
  eval/         golden-set evaluator and report
  provider/     Claude provider against a local Messages API stub
  service/      redaction, slicing, gating and failure-path unit tests

src/test/resources/golden/golden-set.jsonl   35 labelled failure logs
```

No large package reshuffle is needed for a deployable service. Before production, replace `com.example` and the Maven `groupId` with your organization-owned package name.

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
  "providerLatencyMs": 11,
  "redactionsApplied": 0,
  "logLinesIn": 1,
  "logLinesUsed": 1,
  "usage": { "inputTokens": 0, "outputTokens": 0, "estimatedCostUsd": 0.0 },
  "triage": {
    "plainEnglishSummary": "...",
    "likelyRootCause": "...",
    "errorClassification": "SCHEMA_VALIDATION",
    "likelyOwningTeam": "Content Validation Team",
    "severity": "MEDIUM",
    "recommendedNextAction": "...",
    "confidenceScore": 0.86,
    "evidenceFromLog": [
      {
        "lineNumber": 1,
        "text": "2026-05-27T03:14:07Z ERROR Schema validation failed: field rightsWindow.end is null"
      }
    ],
    "humanReviewRequired": false
  }
}
```

### Health

Use Spring Boot Actuator endpoints for platform checks:

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
```

The legacy `GET /health` endpoint remains for simple local checks and returns the active provider/model.

## Run Locally

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
  -d @samples/triage-request.json
```

## Configuration

Runtime configuration is externalized through environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | unset | Use `prod` in AWS |
| `TRIAGE_API_KEY` | `local-dev-key` | Shared API key; required in `prod` |
| `TRIAGE_PROVIDER` | `mock` (`anthropic` in `prod`) | `mock`, `anthropic`, or `bedrock` (stub) |
| `TRIAGE_MODEL_ID` | `mock-deterministic-v1` | Model id reported by the mock and Bedrock providers |
| `ANTHROPIC_API_KEY` | unset | Required when the provider is `anthropic` |
| `TRIAGE_ANTHROPIC_MODEL` | `claude-opus-5-5` | Claude model |
| `TRIAGE_ANTHROPIC_EFFORT` | `low` | `low` to `max`; classification rarely needs more than `low` |
| `TRIAGE_ANTHROPIC_MAX_TOKENS` | `4096` | Output cap; hitting it is treated as a provider failure |
| `TRIAGE_ANTHROPIC_FALLBACKS` | `true` | Let the API re-run a declined request on its recommended fallback model |
| `TRIAGE_ANTHROPIC_INPUT_USD_PER_MTOK` / `_OUTPUT_` | `4.00` / `20.00` | Prices used for the per-request cost estimate |
| `TRIAGE_MAX_BODY_BYTES` | `1048576` | Request size limit |
| `TRIAGE_LOG_MAX_LINES` | `120` | Max sliced lines sent to provider |
| `TRIAGE_LOG_CONTEXT_BEFORE` | `3` | Lines before relevant log event |
| `TRIAGE_LOG_CONTEXT_AFTER` | `6` | Lines after relevant log event |
| `TRIAGE_MIN_CONFIDENCE` | `0.75` | Human-review confidence gate |
| `TRIAGE_FORCE_REVIEW_SEVERITIES` | `HIGH,CRITICAL` | Severities that require review |

## Evaluation

`src/test/resources/golden/golden-set.jsonl` holds 35 labelled failures from a content delivery pipeline: schema and XSD errors, malformed XML, missing S3 objects, checksum and manifest mismatches, timeouts, auth and IAM failures, database conflicts, XSLT and OpenXML failures, a disk-full worker, a prompt-injection attempt hidden in a log line, a log with a credential in it, and logs with no usable signal. Each case is labelled with the expected classification, severity, and whether a human must see it.

The evaluator runs every case through the whole pipeline and scores what a caller would get back:

- **Unsafe auto-approvals** — a case that needed a human went out without review. This must be zero; the build fails otherwise.
- **Classification and severity accuracy.**
- **Review rate and needless reviews** — how much work the gates send to people.
- **Provider latency p50 / p99 and total cost** for the set.

```bash
mvn verify                                        # mock baseline, runs on every build
ANTHROPIC_API_KEY=sk-ant-... mvn verify -Plive-eval   # the same set against Claude
```

Reports are written to `target/eval/mock.md` and `target/eval/claude.md`. CI publishes the mock report on every run; the `ci` workflow's manual run with **live_eval** checked runs the Claude eval using the `ANTHROPIC_API_KEY` repository secret.

The mock is a keyword matcher and scores 28.6% on classification with zero unsafe auto-approvals: it is the floor any model has to beat, and it shows the gates hold even when the provider is bad. The Claude run has to reach 80% with zero unsafe auto-approvals to pass.

## Container Build

Build and run locally:

```bash
docker build -t triage-service:local .
docker run --rm -p 8080:8080 \
  -e TRIAGE_API_KEY=local-dev-key \
  triage-service:local
```

Production profile example:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e TRIAGE_API_KEY='replace-with-secret' \
  -e TRIAGE_PROVIDER=mock \
  triage-service:local
```

## AWS Deployment: ECS Fargate

This service is a good fit for ECS Fargate behind an Application Load Balancer. Use Secrets Manager for `TRIAGE_API_KEY`, CloudWatch Logs for application/audit logs, and an IAM task role for Bedrock once the real provider is wired.

1. Create an ECR repository:

```bash
aws ecr create-repository --repository-name triage-service
```

2. Build and push an image:

```bash
AWS_ACCOUNT_ID=<account-id>
AWS_REGION=us-east-1
IMAGE_TAG=$(git rev-parse --short HEAD)

aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

docker build -t triage-service:"$IMAGE_TAG" .
docker tag triage-service:"$IMAGE_TAG" "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/triage-service:$IMAGE_TAG"
docker push "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/triage-service:$IMAGE_TAG"
```

3. Store the API key in Secrets Manager:

```bash
aws secretsmanager create-secret \
  --name triage-service/api-key \
  --secret-string 'replace-with-a-generated-secret'
```

4. Create IAM roles:

Create or reuse `ecsTaskExecutionRole` with the managed policy `AmazonECSTaskExecutionRolePolicy`. Create a separate task role, for example `triage-service-task-role`, with least-privilege access to any runtime AWS APIs. For Bedrock, grant only the specific model invocation permissions and regions you need.

5. Create CloudWatch log group:

```bash
aws logs create-log-group --log-group-name /ecs/triage-service
```

6. Create the ECS cluster and service:

Use Fargate with `awsvpc` networking, private subnets where possible, and an Application Load Balancer in front of the service. Register the task definition from `deploy/ecs-task-definition.json` after replacing:

```text
<account-id>
<region>
replace-me image
role ARNs
secret ARN
```

7. Configure the ALB target group:

Use HTTP port `8080`, target type `ip`, and health check path:

```text
/actuator/health/readiness
```

8. Runtime environment for the ECS task:

```text
SPRING_PROFILES_ACTIVE=prod
TRIAGE_PROVIDER=anthropic
TRIAGE_ANTHROPIC_MODEL=claude-opus-5-5
TRIAGE_API_KEY=<from Secrets Manager>
ANTHROPIC_API_KEY=<from Secrets Manager>
```

9. Verify deployment:

```bash
curl -s https://<service-domain>/actuator/health
curl -s -X POST https://<service-domain>/v1/triage \
  -H "x-api-key: $TRIAGE_API_KEY" \
  -H 'content-type: application/json' \
  -d @samples/triage-request.json
```

## GitHub Actions CI/CD

`.github/workflows/ci.yml` runs on every push and pull request: build, unit tests, and the mock golden-set eval, with the report in the job summary.

The deploy example lives at `.github/workflows/ci-cd.example.yml`. To activate it, rename it to `ci-cd.yml`.

The pipeline shape is:

1. On pull requests, run tests only.
2. On pushes to `main`, run tests, build the Docker image, push it to ECR, render the ECS task definition with the new image tag, and update the ECS service.
3. Use GitHub OIDC to assume an AWS deployment role. Do not store long-lived AWS access keys in GitHub.

Required GitHub environment/secret:

```text
AWS_DEPLOY_ROLE_ARN=arn:aws:iam::<account-id>:role/github-actions-triage-service-deploy
```

The AWS deploy role should allow:

```text
ecr:GetAuthorizationToken
ecr:BatchCheckLayerAvailability
ecr:CompleteLayerUpload
ecr:InitiateLayerUpload
ecr:PutImage
ecr:UploadLayerPart
ecs:DescribeServices
ecs:DescribeTaskDefinition
ecs:RegisterTaskDefinition
ecs:UpdateService
iam:PassRole for the ECS task roles
```

For production, add branch protection so `main` requires the test job to pass, and put the deploy job behind a GitHub `production` environment with reviewers if you want manual approval.

## Provider Strategy

`LlmProvider` is the only abstraction the pipeline depends on:

```java
public interface LlmProvider {
    String name();
    String model();
    ProviderResult complete(FailureMetadata metadata, LogSlice logSlice);
}
```

`AnthropicLlmProvider` uses the official Anthropic Java SDK. It sends the system and user prompts from `PromptBuilder`, asks for structured output typed as `TriageResult`, and returns token usage and an estimated cost. A `refusal` stop reason becomes `ProviderRefusalException`, and a `max_tokens` stop is treated as a failure rather than parsed as a partial answer; both end in human review.

`MockLlmProvider` is deterministic and runs offline. `BedrockLlmProvider` is still a stub. To finish it, use the SDK's Bedrock backend with the same request, so the gates and the golden set apply unchanged.

## Production Readiness Notes

- Keep `/v1/triage` read-only and authenticated.
- Store secrets in Secrets Manager, not environment files or source control.
- Do not log raw submitted logs; the audit event stores a SHA-256 hash of the raw input.
- Alert on `triage.requests` with `review=true` and on `triage.provider.failures`: a jump in either means the model or the logs have changed.
- Grow the golden set from real failures that were reviewed and corrected, and rerun the live eval before changing the model, effort, or prompts.
- Consider replacing API-key auth with an internal gateway, mTLS, OAuth2 resource server, or IAM-authenticated private API Gateway if this becomes a shared platform service.
