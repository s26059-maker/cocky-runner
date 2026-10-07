package com.cocky.cockyrunner.api;

import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import com.cocky.cockyrunner.runner.DockerRunner;
import com.cocky.cockyrunner.runner.RunOutcome;
import com.cocky.cockyrunner.runner.SubmissionExecution;
import com.cocky.cockyrunner.service.ExecutionService;
import com.cocky.cockyrunner.service.OutputComparator;
import com.cocky.cockyrunner.util.TextTruncator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;

/**
 * Judge/run logic behind {@code /internal/v1/**}. Reuses {@link ExecutionService}
 * (workspace, compile, wrapper-script timing) as-is; only the aggregation rules
 * and the external contract live here. Unlike the frontend's JudgeService it
 * never stops at the first failing case.
 */
@Service
public class ExternalExecutionService {

    /** 64KB, counted in UTF-8 bytes. */
    static final int MAX_INPUT_BYTES = 64 * 1024;
    static final int MAX_OUTPUT_CHARS = 64 * 1024;
    /** /judge captures up to 1MB of stdout for comparison; more than that is a WA. */
    static final int JUDGE_STDOUT_MAX_BYTES = 1024 * 1024;
    /** /run only returns 64KB of stdout, so there's no point capturing more. */
    static final int RUN_STDOUT_MAX_BYTES = 64 * 1024;
    /** Docker rejects --memory below 6 MiB. */
    static final long MIN_DOCKER_MEMORY_KB = 6 * 1024;

    private final ExecutionService executionService;
    private final LanguageSpecRegistry languageSpecRegistry;
    private final Semaphore permits;
    private final OutputComparator comparator = new OutputComparator();

    public ExternalExecutionService(ExecutionService executionService, LanguageSpecRegistry languageSpecRegistry,
                                    RunnerApiProperties properties) {
        this.executionService = executionService;
        this.languageSpecRegistry = languageSpecRegistry;
        this.permits = new Semaphore(properties.maxConcurrent());
    }

    public JudgeApiResponse judge(JudgeRequest request) {
        Language language = parseLanguage(request.language());
        requireSource(request.sourceCode());
        requirePositive(request.timeLimitMs(), "timeLimitMs");
        requirePositive(request.memoryLimitKb(), "memoryLimitKb");
        if (request.testCases() == null || request.testCases().isEmpty()) {
            throw new InvalidExecutionRequestException("testCases must not be empty");
        }
        for (JudgeRequest.TestCaseInput testCase : request.testCases()) {
            if (testCase == null) {
                throw new InvalidExecutionRequestException("testCases must not contain null");
            }
            requireWithinLimit(testCase.input(), "input");
        }
        long timeoutMs = resolveTimeoutMs(language, request.timeLimitMs());

        acquire();
        try {
            return doJudge(language, request, timeoutMs);
        } finally {
            permits.release();
        }
    }

    public RunApiResponse run(RunRequest request) {
        Language language = parseLanguage(request.language());
        requireSource(request.sourceCode());
        requireWithinLimit(request.stdin(), "stdin");
        requirePositive(request.timeLimitMs(), "timeLimitMs");
        requirePositive(request.memoryLimitKb(), "memoryLimitKb");
        long timeoutMs = resolveTimeoutMs(language, request.timeLimitMs());

        acquire();
        try {
            return doRun(language, request, timeoutMs);
        } finally {
            permits.release();
        }
    }

    private JudgeApiResponse doJudge(Language language, JudgeRequest request, long timeoutMs) {
        List<JudgeRequest.TestCaseInput> testCases = request.testCases();
        int total = testCases.size();
        long memoryKb = Math.max(request.memoryLimitKb(), MIN_DOCKER_MEMORY_KB);

        try (SubmissionExecution execution = executionService.prepare(language, request.sourceCode())) {
            if (execution.compilationFailed()) {
                return new JudgeApiResponse(ApiVerdict.CE, 0, total, 0, null,
                        TextTruncator.truncate(execution.compileErrorOutput()));
            }
            failOnInfrastructure(execution);

            int passed = 0;
            long maxTimeMs = 0;
            ApiVerdict firstFailure = null;
            for (JudgeRequest.TestCaseInput testCase : testCases) {
                RunOutcome outcome = executionService.executeLimited(
                        execution, nullToEmpty(testCase.input()), timeoutMs, memoryKb, JUDGE_STDOUT_MAX_BYTES);
                maxTimeMs = Math.max(maxTimeMs, caseTimeMs(outcome.response(), request.timeLimitMs()));

                ApiVerdict verdict = caseVerdict(outcome, nullToEmpty(testCase.expectedOutput()), request.timeLimitMs());
                if (verdict == ApiVerdict.AC) {
                    passed++;
                } else if (firstFailure == null) {
                    firstFailure = verdict;
                }
            }
            return new JudgeApiResponse(firstFailure == null ? ApiVerdict.AC : firstFailure,
                    passed, total, maxTimeMs, null, null);
        }
    }

    private RunApiResponse doRun(Language language, RunRequest request, long timeoutMs) {
        long memoryKb = Math.max(request.memoryLimitKb(), MIN_DOCKER_MEMORY_KB);

        try (SubmissionExecution execution = executionService.prepare(language, request.sourceCode())) {
            if (execution.compilationFailed()) {
                return new RunApiResponse(RunStatus.CE, "", "",
                        TextTruncator.truncate(execution.compileErrorOutput()), 0);
            }
            failOnInfrastructure(execution);

            RunOutcome outcome = executionService.executeLimited(
                    execution, nullToEmpty(request.stdin()), timeoutMs, memoryKb, RUN_STDOUT_MAX_BYTES);
            ExecutionResponse response = outcome.response();
            return new RunApiResponse(runStatus(outcome, request.timeLimitMs()), capOutput(response.stdout()), capOutput(response.stderr()),
                    null, caseTimeMs(response, request.timeLimitMs()));
        }
    }

    private void failOnInfrastructure(SubmissionExecution execution) {
        if (execution.infrastructureFailed()) {
            throw new RunnerInternalException("failed to prepare submission: " + execution.infrastructureFailureDetail());
        }
    }

    private ApiVerdict caseVerdict(RunOutcome outcome, String expectedOutput, int timeLimitMs) {
        ExecutionResponse response = outcome.response();
        return switch (response.status()) {
            case TIMEOUT -> ApiVerdict.TLE;
            case RUNTIME_ERROR -> outcome.oomKilled() ? ApiVerdict.MLE
                    : exceedsTimeLimit(response, timeLimitMs) ? ApiVerdict.TLE : ApiVerdict.RE;
            case SUCCESS -> {
                if (exceedsTimeLimit(response, timeLimitMs)) {
                    yield ApiVerdict.TLE;
                }
                // stdout was cut at the capture cap: it can't match, and must not be compared as if complete
                if (outcome.stdoutTruncated()) {
                    yield ApiVerdict.WA;
                }
                yield comparator.match(expectedOutput, response.stdout()) ? ApiVerdict.AC : ApiVerdict.WA;
            }
            case ERROR, COMPILE_ERROR -> throw new RunnerInternalException(
                    "execution failed with " + response.status() + ": " + response.stderr());
        };
    }

    private RunStatus runStatus(RunOutcome outcome, int timeLimitMs) {
        ExecutionResponse response = outcome.response();
        return switch (response.status()) {
            case TIMEOUT -> RunStatus.TLE;
            case RUNTIME_ERROR -> outcome.oomKilled() ? RunStatus.MLE
                    : exceedsTimeLimit(response, timeLimitMs) ? RunStatus.TLE : RunStatus.RE;
            case SUCCESS -> exceedsTimeLimit(response, timeLimitMs) ? RunStatus.TLE : RunStatus.OK;
            case ERROR, COMPILE_ERROR -> throw new RunnerInternalException(
                    "execution failed with " + response.status() + ": " + response.stderr());
        };
    }

    /**
     * The host timeout (limit x multiplier + startup budget) only exists to kill runaway
     * programs; the actual limit check is on the measured user wall time.
     */
    static boolean exceedsTimeLimit(ExecutionResponse response, long timeLimitMs) {
        return response.userWallMs() != null && response.userWallMs() > timeLimitMs;
    }

    /**
     * The user program's own wall time. A timed-out run never has one (the wrapper
     * didn't finish), so it counts as the full limit; any other missing reading is 0.
     */
    static long caseTimeMs(ExecutionResponse response, long timeLimitMs) {
        if (response.userWallMs() != null) {
            return response.userWallMs();
        }
        return response.status() == ExecutionStatus.TIMEOUT ? timeLimitMs : 0;
    }

    static String capOutput(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text;
        }
        int end = MAX_OUTPUT_CHARS;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private void acquire() {
        if (!permits.tryAcquire()) {
            throw new RunnerBusyException("too many concurrent executions");
        }
    }

    private long resolveTimeoutMs(Language language, int timeLimitMs) {
        LanguageSpec spec = languageSpecRegistry.get(language);
        long timeoutMs = spec.resolvedTimeoutMs(timeLimitMs);
        if (timeoutMs > DockerRunner.MAX_TIMEOUT_MS) {
            throw new InvalidExecutionRequestException("timeLimitMs too large: " + timeLimitMs);
        }
        return timeoutMs;
    }

    private static Language parseLanguage(String language) {
        if (language != null) {
            switch (language) {
                case "python": return Language.PYTHON;
                case "java": return Language.JAVA;
                case "c": return Language.C;
                default: break;
            }
        }
        throw new InvalidExecutionRequestException("unsupported language (expected python|java|c): " + language);
    }

    private static void requireSource(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new InvalidExecutionRequestException("sourceCode must not be blank");
        }
        requireWithinLimit(sourceCode, "sourceCode");
    }

    private static void requireWithinLimit(String value, String field) {
        // n chars are at most 3n UTF-8 bytes (surrogate pairs: 2 chars -> 4 bytes), so only measure when it could matter
        if (value != null && value.length() > MAX_INPUT_BYTES / 3
                && value.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new InvalidExecutionRequestException(field + " must not exceed " + MAX_INPUT_BYTES + " bytes");
        }
    }

    private static void requirePositive(Number value, String field) {
        if (value == null || value.longValue() <= 0) {
            throw new InvalidExecutionRequestException(field + " must be a positive number");
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
