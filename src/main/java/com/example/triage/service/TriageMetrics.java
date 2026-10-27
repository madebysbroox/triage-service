package com.example.triage.service;

import com.example.triage.model.TriageResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Operational metrics, exposed at /actuator/metrics. The review rate and the
 * provider-failure count are the two to alert on: a jump in either means the
 * model or the logs it sees have changed.
 */
@Component
public class TriageMetrics {
    private final MeterRegistry registry;

    public TriageMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(TriageResponse response, boolean providerFailed) {
        Counter.builder("triage.requests")
                .tag("provider", response.provider())
                .tag("classification", response.triage().errorClassification().name())
                .tag("severity", response.triage().severity().name())
                .tag("review", Boolean.toString(response.triage().humanReviewRequired()))
                .register(registry)
                .increment();
        Timer.builder("triage.provider.latency")
                .tag("provider", response.provider())
                .publishPercentiles(0.5, 0.99)
                .register(registry)
                .record(response.providerLatencyMs(), TimeUnit.MILLISECONDS);
        registry.counter("triage.provider.tokens", "direction", "input").increment(response.usage().inputTokens());
        registry.counter("triage.provider.tokens", "direction", "output").increment(response.usage().outputTokens());
        registry.counter("triage.provider.cost.usd").increment(response.usage().estimatedCostUsd());
        if (providerFailed) {
            registry.counter("triage.provider.failures", "provider", response.provider()).increment();
        }
    }
}
