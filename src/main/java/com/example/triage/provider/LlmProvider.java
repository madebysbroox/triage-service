package com.example.triage.provider;

import com.example.triage.model.FailureMetadata;
import com.example.triage.service.LogSlice;

public interface LlmProvider {
    String name();
    String model();
    ProviderResult complete(FailureMetadata metadata, LogSlice logSlice);
}
