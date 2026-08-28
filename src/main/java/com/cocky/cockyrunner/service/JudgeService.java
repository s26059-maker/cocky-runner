package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.JudgeResult;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.domain.Problem;
import com.cocky.cockyrunner.domain.TestCase;
import com.cocky.cockyrunner.domain.Verdict;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.ProblemNotFoundException;
import com.cocky.cockyrunner.repository.ProblemRepository;
import com.cocky.cockyrunner.runner.SubmissionExecution;
import com.cocky.cockyrunner.util.TextTruncator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs a submission's test cases in order, stopping at the first non-AC result.
 *
 * <p>Compiles the submission exactly once via {@link ExecutionService#prepare(Language, String)}
 * and reuses that {@link SubmissionExecution} for every test case, closing it when
 * done (try-with-resources) regardless of outcome. If compilation fails, the test
 * case loop never runs and the submission is judged {@link Verdict#CE} directly.
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final ProblemRepository problemRepository;
    private final ExecutionService executionService;
    private final LanguageSpecRegistry languageSpecRegistry;
    private final OutputComparator outputComparator = new OutputComparator();

    public JudgeService(ProblemRepository problemRepository, ExecutionService executionService,
                         LanguageSpecRegistry languageSpecRegistry) {
        this.problemRepository = problemRepository;
        this.executionService = executionService;
        this.languageSpecRegistry = languageSpecRegistry;
    }

    public JudgeResult judge(String problemId, Language language, String code) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException("problem not found: " + problemId));

        long timeoutMs = resolveTimeoutMs(problem, language);

        try (SubmissionExecution execution = executionService.prepare(language, code)) {
            if (execution.compilationFailed()) {
                String errorOutput = TextTruncator.truncate(execution.compileErrorOutput());
                return new JudgeResult(Verdict.CE, 0, problem.testCases().size(), null, 0, errorOutput);
            }
            if (execution.infrastructureFailed()) {
                // Infra failure happened before any test case was picked, so there's no
                // per-test-case "is it a public sample" context to gate stderr exposure
                // on - default to not exposing it to the client, same as the conservative
                // default for ERROR results in extractErrorOutput(). The detail still goes
                // to the server log (at error level, with full context) since this is an
                // internal failure an operator needs to see, not a user-code problem.
                log.error("infrastructure failure preparing submission for problem {} language {}; " +
                        "short-circuiting to ERROR instead of running {} test case(s): {}",
                        problemId, language, problem.testCases().size(), execution.infrastructureFailureDetail());
                return new JudgeResult(Verdict.ERROR, 0, problem.testCases().size(), null, 0, null);
            }
            return runTestCases(problem, execution, timeoutMs);
        }
    }

    /**
     * Runs every test case against an already-prepared (and, if needed, already
     * compiled) submission, stopping at the first non-AC result.
     */
    private JudgeResult runTestCases(Problem problem, SubmissionExecution execution, long timeoutMs) {
        List<TestCase> testCases = problem.testCases();
        int passedCount = 0;
        long maxExecutionTimeMs = 0;

        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            ExecutionResponse response = executionService.execute(execution, testCase.input(), timeoutMs);

            maxExecutionTimeMs = Math.max(maxExecutionTimeMs, response.executionTimeMs());

            Verdict caseVerdict = judgeCase(testCase, response);
            if (caseVerdict != Verdict.AC) {
                String errorOutput = extractErrorOutput(caseVerdict, testCase, response);
                return new JudgeResult(caseVerdict, passedCount, testCases.size(), i + 1, maxExecutionTimeMs, errorOutput);
            }
            passedCount++;
        }

        return new JudgeResult(Verdict.AC, passedCount, testCases.size(), null, maxExecutionTimeMs, null);
    }

    /**
     * Resolves the wall-clock timeout for every test case of this submission:
     * the problem's base time limit scaled by the language's
     * {@link LanguageSpec#timeLimitMultiplier()}. Computed once per submission,
     * up front, so every test case (and the CE short-circuit, which never uses it)
     * sees the same value. Does not apply to the compile step, which always uses
     * the fixed {@code DockerRunner.COMPILE_TIMEOUT_MS} regardless of language.
     */
    private long resolveTimeoutMs(Problem problem, Language language) {
        LanguageSpec spec = languageSpecRegistry.get(language);
        long timeoutMs = Math.round(problem.timeLimitMs() * spec.timeLimitMultiplier());
        log.info("resolved run timeout for problem {} language {}: {}ms (base {}ms x multiplier {})",
                problem.id(), language, timeoutMs, problem.timeLimitMs(), spec.timeLimitMultiplier());
        return timeoutMs;
    }

    private Verdict judgeCase(TestCase testCase, ExecutionResponse response) {
        return switch (response.status()) {
            case TIMEOUT -> Verdict.TLE;
            case RUNTIME_ERROR -> Verdict.RE;
            case ERROR -> Verdict.ERROR;
            case SUCCESS -> outputComparator.match(testCase.expectedOutput(), response.stdout())
                    ? Verdict.AC
                    : Verdict.WA;
            // Never reached here: a compile failure is caught by compilationFailed() in
            // judge() before the test case loop starts, so no per-test-case
            // ExecutionResponse from a ready SubmissionExecution can carry this status.
            case COMPILE_ERROR -> throw new IllegalStateException(
                    "unexpected COMPILE_ERROR status from a SubmissionExecution that reported compilationFailed() == false");
        };
    }

    /**
     * The sole gate on exposing stderr: only a sample-case RE/ERROR passes.
     */
    private String extractErrorOutput(Verdict verdict, TestCase testCase, ExecutionResponse response) {
        if (!testCase.sample()) {
            return null;
        }
        if (verdict != Verdict.RE && verdict != Verdict.ERROR) {
            return null;
        }
        String stderr = response.stderr();
        if (stderr == null || stderr.isBlank()) {
            return null;
        }
        return TextTruncator.truncate(stderr);
    }
}
