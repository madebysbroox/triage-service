# Triage Service

A read-only Spring Boot service for AI-enriched classification and summarization of failed operational workflows across ingestion, packaging, validation, delivery, and other backend services.

The service accepts a failed workflow log and/or failure metadata, redacts sensitive values, slices the log down to relevant evidence windows, calls a provider-agnostic `LlmProvider`, validates the structured triage result, applies deterministic human-review gates, and emits an audit log.

## Current Shape

This project already follows the standard Spring Boot layout:

```text
src/main/java/com/example/triage
  TriageServiceApplication.java
  config/       API key, request size, bound configuration
  controller/   REST controllers and exception handling
  model/        request/response DTOs and enums
  provider/     provider abstraction, mock provider, Bedrock adapter stub
  service/      redaction, log slicing, validation/gating, audit, orchestration
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
| `TRIAGE_PROVIDER` | `mock` | `mock` locally, `bedrock` in AWS |
| `TRIAGE_MODEL_ID` | `mock-deterministic-v1` | Provider model identifier |
| `TRIAGE_MAX_BODY_BYTES` | `1048576` | Request size limit |
| `TRIAGE_LOG_MAX_LINES` | `120` | Max sliced lines sent to provider |
| `TRIAGE_LOG_CONTEXT_BEFORE` | `3` | Lines before relevant log event |
| `TRIAGE_LOG_CONTEXT_AFTER` | `6` | Lines after relevant log event |
| `TRIAGE_MIN_CONFIDENCE` | `0.75` | Human-review confidence gate |
| `TRIAGE_FORCE_REVIEW_SEVERITIES` | `HIGH,CRITICAL` | Severities that require review |

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
TRIAGE_PROVIDER=bedrock
TRIAGE_MODEL_ID=<bedrock-model-id>
TRIAGE_API_KEY=<from Secrets Manager>
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

The example workflow lives at `.github/workflows/ci-cd.example.yml`. To activate it, rename it to `ci-cd.yml`.

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

`MockLlmProvider` is deterministic and runs offline. `BedrockLlmProvider` is intentionally still a stub. To finish Bedrock support, add the AWS SDK Bedrock Runtime client, serialize prompts with `PromptBuilder`, parse the model response into `TriageResult`, and continue to let `TriageValidationService` enforce deterministic gates.

## Production Readiness Notes

- Keep `/v1/triage` read-only and authenticated.
- Store secrets in Secrets Manager, not environment files or source control.
- Do not log raw submitted logs; the audit event stores a SHA-256 hash of the raw input.
- Add Micrometer counters for classification, severity, review rate, and provider latency.
- Add golden-set tests with representative failed workflow logs before switching the production provider from `mock` to `bedrock`.
- Consider replacing API-key auth with an internal gateway, mTLS, OAuth2 resource server, or IAM-authenticated private API Gateway if this becomes a shared platform service.
