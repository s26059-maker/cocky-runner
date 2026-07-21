package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.dto.ExecutionRequest;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import com.cocky.cockyrunner.runner.DockerRunner;
import org.springframework.stereotype.Service;

/**
 * Pure execution core with no HTTP dependency, so it can be reused as-is by the
 * future grading engine. The grading engine will call {@link #execute(ExecutionRequest, long)}
 * with a per-test-case timeout; the HTTP API keeps using the global default via
 * {@link #execute(ExecutionRequest)} since the timeout must never be caller-controlled
 * over HTTP.
 */
@Service
public class ExecutionService {

    private static final long MAX_TIMEOUT_MS = 30_000;

    private final DockerRunner dockerRunner;
    private final long defaultTimeoutMs;

    public ExecutionService(DockerRunner dockerRunner, DockerProperties properties) {
        this.dockerRunner = dockerRunner;
        this.defaultTimeoutMs = properties.timeoutSeconds() * 1000L;
    }

    public ExecutionResponse execute(ExecutionRequest request) {
        return execute(request, defaultTimeoutMs);
    }

    public ExecutionResponse execute(ExecutionRequest request, long timeoutMs) {
        if (request.code() == null || request.code().isBlank()) {
            throw new InvalidExecutionRequestException("code must not be blank");
        }
        validateTimeout(timeoutMs);
        Language language = parseLanguage(request.language());
        return dockerRunner.run(language, request.code(), request.stdin(), timeoutMs);
    }

    private void validateTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new InvalidExecutionRequestException("timeoutMs must be positive: " + timeoutMs);
        }
        if (timeoutMs > MAX_TIMEOUT_MS) {
            throw new InvalidExecutionRequestException("timeoutMs must not exceed " + MAX_TIMEOUT_MS + ": " + timeoutMs);
        }
    }

    private Language parseLanguage(String language) {
        if (language == null || language.isBlank()) {
            throw new InvalidExecutionRequestException("language must not be blank");
        }
        try {
            return Language.valueOf(language.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidExecutionRequestException("unsupported language: " + language);
        }
    }
}
