package com.cocky.cockyrunner.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the external ({@code /internal/**}) API. Validated in the
 * constructor so a missing token fails application startup instead of leaving the
 * endpoints unauthenticated (or permanently locked) at runtime.
 */
@ConfigurationProperties(prefix = "runner.api")
public record RunnerApiProperties(String token, int maxConcurrent) {

    public RunnerApiProperties {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "runner.api.token must be set (env RUNNER_TOKEN) - refusing to start without it");
        }
        if (maxConcurrent < 1) {
            throw new IllegalStateException("runner.api.max-concurrent must be >= 1: " + maxConcurrent);
        }
    }
}
