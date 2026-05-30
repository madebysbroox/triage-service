package com.example.triage.provider;

import com.example.triage.config.TriageProperties;
import com.example.triage.model.FailureMetadata;
import com.example.triage.service.LogSlice;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "triage", name = "provider", havingValue = "bedrock")
public class BedrockLlmProvider implements LlmProvider {
    private final TriageProperties properties;

    public BedrockLlmProvider(TriageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "bedrock";
    }

    @Override
    public String model() {
        return properties.model().id();
    }

    @Override
    public ProviderResult complete(FailureMetadata metadata, LogSlice logSlice) {
        throw new UnsupportedOperationException("Bedrock adapter stub: wire AWS SDK BedrockRuntimeClient here for Slice 1.");
    }
}
