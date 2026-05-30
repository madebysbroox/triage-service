package com.example.triage;

import com.example.triage.model.*;
import com.example.triage.service.RedactionService;
import com.example.triage.service.RedactionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TriageApplicationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RedactionService redactionService;

    @Test
    void healthReturnsMockProvider() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.provider").value("mock"));
    }

    @Test
    void rejectsMissingApiKey() throws Exception {
        mockMvc.perform(post("/v1/triage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"log\":\"ERROR something failed\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void classifiesSchemaValidation() throws Exception {
        TriageRequest request = new TriageRequest(
                "2026-05-27T03:14:07Z ERROR Schema validation failed: field rightsWindow.end is null",
                new FailureMetadata(
                        "WF-2026-0099831",
                        FailureStage.VALIDATION,
                        "content-prod",
                        "validation-worker",
                        "prod",
                        OffsetDateTime.parse("2026-05-27T03:14:07Z")
                )
        );

        String response = mockMvc.perform(post("/v1/triage")
                        .header("x-api-key", "local-dev-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.triage.errorClassification").value("SCHEMA_VALIDATION"))
                .andExpect(jsonPath("$.triage.likelyOwningTeam").value("Content Validation Team"))
                .andExpect(jsonPath("$.triage.humanReviewRequired").value(false))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("triage").get("evidenceFromLog").get(0).get("lineNumber").asInt()).isEqualTo(1);
    }

    @Test
    void lowConfidenceUnknownForcesHumanReview() throws Exception {
        String body = "{\"log\":\"INFO harmless line with no signature\"}";
        mockMvc.perform(post("/v1/triage")
                        .header("x-api-key", "local-dev-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triage.errorClassification").value("UNKNOWN"))
                .andExpect(jsonPath("$.triage.humanReviewRequired").value(true));
    }

    @Test
    void redactsSecretsAndEmail() {
        RedactionResult result = redactionService.redact("email jane@example.com password=supersecret bearer abc.def.ghi");
        assertThat(result.text()).contains("[REDACTED_EMAIL]");
        assertThat(result.text()).contains("password=[REDACTED_SECRET]");
        assertThat(result.text()).contains("Bearer [REDACTED_TOKEN]");
        assertThat(result.redactionsApplied()).isGreaterThanOrEqualTo(3);
    }
}
