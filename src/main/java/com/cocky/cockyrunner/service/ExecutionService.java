package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.dto.ExecutionRequest;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import com.cocky.cockyrunner.runner.DockerRunner;
import org.springframework.stereotype.Service;

/**
 * Pure execution core with no HTTP dependency, so it can be reused as-is by the
 * future grading engine.
 */
@Service
public class ExecutionService {

    private final DockerRunner dockerRunner;

    public ExecutionService(DockerRunner dockerRunner) {
        this.dockerRunner = dockerRunner;
    }

    public ExecutionResponse execute(ExecutionRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new InvalidExecutionRequestException("code must not be blank");
        }
        Language language = parseLanguage(request.language());
        return dockerRunner.run(language, request.code(), request.stdin());
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
