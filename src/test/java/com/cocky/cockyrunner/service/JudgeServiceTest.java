package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageDockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.JudgeResult;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.Problem;
import com.cocky.cockyrunner.domain.TestCase;
import com.cocky.cockyrunner.domain.Verdict;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.ProblemNotFoundException;
import com.cocky.cockyrunner.repository.ProblemRepository;
import com.cocky.cockyrunner.runner.SubmissionExecution;
import com.cocky.cockyrunner.util.TextTruncator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JudgeServiceTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ExecutionService executionService = mock(ExecutionService.class);
    private final LanguageSpecRegistry languageSpecRegistry = new LanguageSpecRegistry(testDockerProperties());
    private JudgeService judgeService;

    @BeforeEach
    void setUp() {
        // Every test below that doesn't care about the exact resolved timeout stubs
        // executionService.execute(...) with anyLong() rather than an exact eq(...),
        // so this shared 100ms-per-language budget doesn't need to be threaded through
        // them. Tests that DO assert on the resolved timeout account for it explicitly;
        // resolvedTimeoutMs_usesEachLanguagesOwnStartupBudget below covers per-language
        // budgets differing from one another.
        judgeService = new JudgeService(problemRepository, executionService, languageSpecRegistry);
    }

    private static DockerProperties testDockerProperties() {
        return new DockerProperties(
                Map.of(
                        Language.C, new LanguageDockerProperties("gcc:14", 100),
                        Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 100)
                ),
                5, "256m", 1.0, 64, 65536, "build/judge-work-test");
    }

    /** A ready-to-run (compiled successfully, no infra failure) mock execution. */
    private SubmissionExecution readyExecution() {
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(false);
        when(execution.infrastructureFailed()).thenReturn(false);
        return execution;
    }

    private void stubPrepare(SubmissionExecution execution) {
        when(executionService.prepare(any(Language.class), anyString())).thenReturn(execution);
    }

    @Test
    void allCasesPass_returnsAc() {
        TestCase tc1 = new TestCase("in1", "out1", true);
        TestCase tc2 = new TestCase("in2", "out2", false);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc1, tc2));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out1", 100))
                .thenReturn(success("out2", 300));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.AC);
        assertThat(result.passedCount()).isEqualTo(2);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.failedCaseNumber()).isNull();
        assertThat(result.maxExecutionTimeMs()).isEqualTo(300);
        verify(execution).close();
    }

    @Test
    void secondCaseFails_returnsWaAndStopsBeforeThirdCase() {
        TestCase tc1 = new TestCase("in1", "out1", true);
        TestCase tc2 = new TestCase("in2", "expected2", false);
        TestCase tc3 = new TestCase("in3", "out3", false);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc1, tc2, tc3));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out1", 100))
                .thenReturn(success("wrong-output", 150));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.WA);
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.failedCaseNumber()).isEqualTo(2);
        verify(executionService, times(2)).execute(eq(execution), anyString(), anyLong());
        verify(execution).close();
    }

    @Test
    void timeoutStatus_returnsTle() {
        assertSingleCaseVerdict(ExecutionStatus.TIMEOUT, -1, Verdict.TLE);
    }

    @Test
    void runtimeErrorStatus_returnsRe() {
        assertSingleCaseVerdict(ExecutionStatus.RUNTIME_ERROR, 1, Verdict.RE);
    }

    @Test
    void errorStatus_returnsError() {
        assertSingleCaseVerdict(ExecutionStatus.ERROR, -1, Verdict.ERROR);
    }

    private void assertSingleCaseVerdict(ExecutionStatus status, int exitCode, Verdict expectedVerdict) {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(ExecutionResponse.withoutTiming(status, "", "", exitCode, 500));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.verdict()).isEqualTo(expectedVerdict);
        assertThat(result.passedCount()).isEqualTo(0);
        assertThat(result.failedCaseNumber()).isEqualTo(1);
    }

    @Test
    void passesLanguageCodeStdinAndResolvedTimeoutToExecutionService() {
        TestCase tc = new TestCase("stdin-in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 1234, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out", 50));

        judgeService.judge("p1", Language.C, "print(1)");

        ArgumentCaptor<Language> languageCaptor = ArgumentCaptor.forClass(Language.class);
        ArgumentCaptor<String> prepareCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(executionService).prepare(languageCaptor.capture(), prepareCodeCaptor.capture());
        assertThat(languageCaptor.getValue()).isEqualTo(Language.C);
        assertThat(prepareCodeCaptor.getValue()).isEqualTo("print(1)");

        ArgumentCaptor<String> stdinCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(executionService).execute(eq(execution), stdinCaptor.capture(), timeoutCaptor.capture());
        assertThat(stdinCaptor.getValue()).isEqualTo("stdin-in");
        // C's timeLimitMultiplier is 1.0 and this fixture's C budget is 100ms, so the
        // resolved timeout is the problem's base limit plus that budget.
        assertThat(timeoutCaptor.getValue()).isEqualTo(1334L);
    }

    @Test
    void pythonMultiplier_scalesTimeoutByThree() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 1000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out", 50));

        judgeService.judge("p1", Language.PYTHON, "code");

        // PYTHON's timeLimitMultiplier is 3.0 and this fixture's PYTHON budget is 100ms:
        // 1000*3 + 100 = 3100.
        verify(executionService).execute(eq(execution), anyString(), eq(3100L));
    }

    @Test
    void cMultiplier_leavesTimeoutUnscaled() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 1000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out", 50));

        judgeService.judge("p1", Language.C, "code");

        // C's timeLimitMultiplier is 1.0 and this fixture's C budget is 100ms:
        // 1000*1 + 100 = 1100.
        verify(executionService).execute(eq(execution), anyString(), eq(1100L));
    }

    @Test
    void resolvedTimeoutMs_usesEachLanguagesOwnStartupBudget() {
        DockerProperties propertiesWithDistinctBudgets = new DockerProperties(
                Map.of(
                        Language.C, new LanguageDockerProperties("gcc:14", 200),
                        Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 5000)
                ),
                5, "256m", 1.0, 64, 65536, "build/judge-work-test");
        LanguageSpecRegistry registryWithDistinctBudgets = new LanguageSpecRegistry(propertiesWithDistinctBudgets);
        JudgeService judgeServiceWithDistinctBudgets =
                new JudgeService(problemRepository, executionService, registryWithDistinctBudgets);
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 1000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out", 50));

        judgeServiceWithDistinctBudgets.judge("p1", Language.C, "code");
        // C: multiplier 1.0, budget 200 -> 1000*1 + 200 = 1200ms.
        verify(executionService).execute(eq(execution), anyString(), eq(1200L));

        judgeServiceWithDistinctBudgets.judge("p1", Language.PYTHON, "code");
        // PYTHON: multiplier 3.0, budget 5000 -> 1000*3 + 5000 = 8000ms - different from
        // C's resolved timeout above, proving each language's own configured budget is
        // used rather than one shared value.
        verify(executionService).execute(eq(execution), anyString(), eq(8000L));
    }

    @Test
    void reOnSampleCase_includesErrorOutput() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(ExecutionResponse.withoutTiming(ExecutionStatus.RUNTIME_ERROR, "", "Traceback...\nValueError: invalid literal", 1, 50));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.RE);
        assertThat(result.errorOutput()).isEqualTo("Traceback...\nValueError: invalid literal");
    }

    @Test
    void reOnHiddenCase_errorOutputIsNull() {
        TestCase tc = new TestCase("in", "out", false);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(ExecutionResponse.withoutTiming(ExecutionStatus.RUNTIME_ERROR, "", "some stderr the client must never see", 1, 50));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.RE);
        assertThat(result.errorOutput()).isNull();
    }

    @Test
    void errorOnSampleCase_includesErrorOutput() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(ExecutionResponse.withoutTiming(ExecutionStatus.ERROR, "", "failed to run docker: boom", -1, 50));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.ERROR);
        assertThat(result.errorOutput()).isEqualTo("failed to run docker: boom");
    }

    @Test
    void wa_errorOutputIsNullAndStdoutNeverLeaks() {
        TestCase tc = new TestCase("in", "expected", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(ExecutionResponse.withoutTiming(ExecutionStatus.SUCCESS, "wrong-output", "", 0, 50));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.WA);
        assertThat(result.errorOutput()).isNull();
    }

    @Test
    void timeoutOnSampleCase_errorOutputIsNull() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(ExecutionResponse.withoutTiming(ExecutionStatus.TIMEOUT, "", "", -1, 2000));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.TLE);
        assertThat(result.errorOutput()).isNull();
    }

    @Test
    void errorOutputExceedingLimit_isTruncated() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        String longStderr = "x".repeat(TextTruncator.MAX_LENGTH + 1000);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(ExecutionResponse.withoutTiming(ExecutionStatus.RUNTIME_ERROR, "", longStderr, 1, 50));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.errorOutput()).hasSizeLessThan(longStderr.length());
        assertThat(result.errorOutput()).contains("truncated");
    }

    @Test
    void compileFailure_returnsCeWithoutRunningTestCases() {
        TestCase tc1 = new TestCase("in1", "out1", true);
        TestCase tc2 = new TestCase("in2", "out2", false);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc1, tc2));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(true);
        when(execution.compileErrorOutput()).thenReturn("main.c:1:1: error: expected ';'");
        stubPrepare(execution);

        JudgeResult result = judgeService.judge("p1", Language.C, "int main() {");

        assertThat(result.verdict()).isEqualTo(Verdict.CE);
        assertThat(result.passedCount()).isEqualTo(0);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.failedCaseNumber()).isNull();
        assertThat(result.errorOutput()).isEqualTo("main.c:1:1: error: expected ';'");
        verify(executionService, never()).execute(any(SubmissionExecution.class), anyString(), anyLong());
        verify(execution).close();
    }

    @Test
    void compileFailureOnHiddenOnlyProblem_stillIncludesErrorOutput() {
        // CE exposes the compiler's stderr regardless of sample status - unlike RE/ERROR,
        // it's about the submitted code itself, not about any test case's data.
        TestCase hiddenOnly = new TestCase("in", "out", false);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(hiddenOnly));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(true);
        when(execution.compileErrorOutput()).thenReturn("compile error detail");
        stubPrepare(execution);

        JudgeResult result = judgeService.judge("p1", Language.C, "bad code");

        assertThat(result.verdict()).isEqualTo(Verdict.CE);
        assertThat(result.errorOutput()).isEqualTo("compile error detail");
    }

    @Test
    void infrastructureFailure_returnsErrorWithoutRunningTestCases() {
        TestCase tc1 = new TestCase("in1", "out1", true);
        TestCase tc2 = new TestCase("in2", "out2", false);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc1, tc2));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(false);
        when(execution.infrastructureFailed()).thenReturn(true);
        stubPrepare(execution);

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.ERROR);
        assertThat(result.passedCount()).isEqualTo(0);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.failedCaseNumber()).isNull();
        assertThat(result.errorOutput()).isNull();
        verify(executionService, never()).execute(any(SubmissionExecution.class), anyString(), anyLong());
        verify(execution).close();
    }

    // --- close() guarantee tests ---------------------------------------------------
    // try-with-resources already guarantees these at the language level; these tests
    // exist to catch a future refactor that accidentally drops the try-with-resources
    // (or otherwise breaks the guarantee) rather than to prove today's behavior.

    @Test
    void close_isCalledAfterNormalCompletion() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out", 100));

        judgeService.judge("p1", Language.C, "code");

        verify(execution).close();
    }

    @Test
    void close_isCalledWhenExecuteThrowsDuringTestCaseLoop() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenThrow(new RuntimeException("docker daemon unreachable"));

        assertThatThrownBy(() -> judgeService.judge("p1", Language.C, "code"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("docker daemon unreachable");

        verify(execution).close();
    }

    @Test
    void close_isCalledOnCompileFailureShortCircuit() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(true);
        when(execution.compileErrorOutput()).thenReturn("error: expected ';'");
        stubPrepare(execution);

        judgeService.judge("p1", Language.C, "int main() {");

        verify(execution).close();
    }

    @Test
    void close_isCalledOnInfrastructureFailureShortCircuit() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(false);
        when(execution.infrastructureFailed()).thenReturn(true);
        stubPrepare(execution);

        judgeService.judge("p1", Language.C, "code");

        verify(execution).close();
    }

    @Test
    void unknownProblemId_throwsProblemNotFoundException() {
        when(problemRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> judgeService.judge("nope", Language.PYTHON, "code"))
                .isInstanceOf(ProblemNotFoundException.class);

        verifyNoInteractions(executionService);
    }

    private ExecutionResponse success(String stdout, long executionTimeMs) {
        return ExecutionResponse.withoutTiming(ExecutionStatus.SUCCESS, stdout, "", 0, executionTimeMs);
    }

    private ExecutionResponse successWithTiming(String stdout, long executionTimeMs, long userWallMs, long userCpuMs) {
        return new ExecutionResponse(ExecutionStatus.SUCCESS, stdout, "", 0, executionTimeMs, userWallMs, userCpuMs);
    }

    @Test
    void userWallAndCpuMs_areCarriedOverFromTheSlowestCase() {
        TestCase tc1 = new TestCase("in1", "out1", true);
        TestCase tc2 = new TestCase("in2", "out2", false);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc1, tc2));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(successWithTiming("out1", 100, 60, 40))
                .thenReturn(successWithTiming("out2", 300, 250, 200));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.maxExecutionTimeMs()).isEqualTo(300);
        // Must come from the same (second) test case maxExecutionTimeMs came from,
        // not e.g. the max of userWallMs/userCpuMs independently across cases.
        assertThat(result.userWallMs()).isEqualTo(250L);
        assertThat(result.userCpuMs()).isEqualTo(200L);
    }

    @Test
    void aSlowerCaseWithFailedTimingParse_isNotOverwrittenByALaterShorterCase() {
        // Regression for the hasSnapshot flag: userWallMs == null is also the
        // normal shape of "this case's timing just didn't parse", not only "no
        // snapshot taken yet" - a null-check alone would keep re-triggering on
        // every later case (since the local userWallMs stays null) until a
        // non-null one showed up, letting case 2's shorter, successfully-parsed
        // time silently overwrite case 1's genuinely slower one.
        TestCase tc1 = new TestCase("in1", "out1", true);
        TestCase tc2 = new TestCase("in2", "out2", false);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc1, tc2));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out1", 300))              // slower, but timing parse failed
                .thenReturn(successWithTiming("out2", 100, 80, 50)); // faster, timing parsed fine

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.maxExecutionTimeMs()).isEqualTo(300);
        assertThat(result.userWallMs()).isNull();
        assertThat(result.userCpuMs()).isNull();
    }

    @Test
    void userWallAndCpuMs_nullWhenTimingCouldNotBeDetermined() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out", 100));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.userWallMs()).isNull();
        assertThat(result.userCpuMs()).isNull();
    }
}
