package com.example.triage.controller;

import com.example.triage.model.HealthResponse;
import com.example.triage.provider.LlmProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final LlmProvider provider;

    public HealthController(LlmProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", provider.name(), provider.model());
    }
}
