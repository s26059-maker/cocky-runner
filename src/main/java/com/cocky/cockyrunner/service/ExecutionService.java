package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.dto.ExecutionRequest;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import com.cocky.cockyrunner.runner.DockerRunner;
import com.cocky.cockyrunner.runner.SubmissionExecution;
import com.cocky.cockyrunner.util.TextTruncator;
import org.springframework.stereotype.Service;

/**
 * Pure execution core with no HTTP dependency, so it can be reused as-is by the
 * grading engine ({@link com.cocky.cockyrunner.service.JudgeService}). This is the
 * single entry point into {@link DockerRunner}: callers outside this class never
 * touch {@link DockerRunner} directly.
 *
 * <p>{@link #prepare(Language, String)} and {@link #execute(SubmissionExecution, String, long)}
 * split submission setup (workspace + one-time compile) from running individual
 * test cases against it, so a caller that needs to run many test cases against the
 * same submission - {@link com.cocky.cockyrunner.service.JudgeService} - calls
 * {@code prepare} exactly once and {@code execute} once per test case. The
 * single-shot {@link #execute(ExecutionRequest)}/{@link #execute(ExecutionRequest, long)}
 * methods used by the HTTP API keep their original signatures and are now
 * implemented as prepare -> execute -> close internally.
 */
@Service
public class ExecutionService {

    private final DockerRunner dockerRunner;
    private final LanguageSpecRegistry languageSpecRegistry;
    private final long defaultTimeoutMs;

    public ExecutionService(DockerRunner dockerRunner, LanguageSpecRegistry languageSpecRegistry,
                             DockerProperties properties) {
        this.dockerRunner = dockerRunner;
        this.languageSpecRegistry = languageSpecRegistry;
        this.defaultTimeoutMs = properties.timeoutSeconds() * 1000L;
    }

    public ExecutionResponse execute(ExecutionRequest request) {
        return execute(request, defaultTimeoutMs);
    }

    public ExecutionResponse execute(ExecutionRequest request, long timeoutMs) {
        validateTimeout(timeoutMs);
        Language language = parseLanguage(request.language());
        try (SubmissionExecution execution = prepare(language, request.code())) {
            if (execution.compilationFailed()) {
                String compileErrorOutput = TextTruncator.truncate(execution.compileErrorOutput());
                return ExecutionResponse.withoutTiming(ExecutionStatus.COMPILE_ERROR, "", compileErrorOutput, -1, 0);
            }
            return execute(execution, request.stdin(), timeoutMs);
        }
    }

    /**
     * Creates a per-submission workspace and, if the language requires it,
     * compiles the code once. Callers must {@link SubmissionExecution#close()} the
     * result (try-with-resources) once done running test cases against it.
     */
    public SubmissionExecution prepare(Language language, String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidExecutionRequestException("code must not be blank");
        }
        LanguageSpec spec = languageSpecRegistry.get(language);
        return dockerRunner.prepareSubmission(spec, code);
    }

    /**
     * Runs one test case's stdin against an already-{@link #prepare}d submission.
     * Must not be called when {@link SubmissionExecution#compilationFailed()} is true.
     */
    public ExecutionResponse execute(SubmissionExecution execution, String stdin, long timeoutMs) {
        if (execution.compilationFailed()) {
            throw new IllegalStateException("cannot execute a test case: compilation failed for this submission");
        }
        validateTimeout(timeoutMs);
        return execution.run(stdin, timeoutMs);
    }

    private void validateTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new InvalidExecutionRequestException("timeoutMs must be positive: " + timeoutMs);
        }
        // DockerRunner is the policy owner for this upper bound (it's the actual
        // infrastructure constraint) - referenced here rather than duplicated so the
        // two can never drift apart. DockerRunner.run() re-validates independently,
        // so this check is a fast-fail rather than the only line of defense.
        if (timeoutMs > DockerRunner.MAX_TIMEOUT_MS) {
            throw new InvalidExecutionRequestException(
                    "timeoutMs must not exceed " + DockerRunner.MAX_TIMEOUT_MS + ": " + timeoutMs);
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
