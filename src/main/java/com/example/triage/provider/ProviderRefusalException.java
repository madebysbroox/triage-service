package com.example.triage.provider;

/**
 * The model declined the request (stop reason "refusal"). The service turns this
 * into an UNKNOWN result that a human must review, like any other provider failure.
 */
public class ProviderRefusalException extends RuntimeException {
    public ProviderRefusalException(String message) {
        super(message);
    }
}
