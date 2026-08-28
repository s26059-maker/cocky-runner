package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.config.DockerProperties;
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
        judgeService = new JudgeService(problemRepository, executionService, languageSpecRegistry);
    }

    private static DockerProperties testDockerProperties() {
        return new DockerProperties(
                Map.of("c", "gcc:14", "python", "python:3.11-slim"),
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
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
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
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
                .thenReturn(success("out1", 100))
                .thenReturn(success("wrong-output", 150));

        JudgeResult result = judgeService.judge("p1", Language.C, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.WA);
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.failedCaseNumber()).isEqualTo(2);
        verify(executionService, times(2)).execute(eq(execution), anyString(), eq(2000L));
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
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
                .thenReturn(new ExecutionResponse(status, "", "", exitCode, 500));

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
        // C's timeLimitMultiplier is 1.0, so the resolved timeout equals the problem's base limit.
        assertThat(timeoutCaptor.getValue()).isEqualTo(1234L);
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

        verify(executionService).execute(eq(execution), anyString(), eq(3000L));
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

        verify(executionService).execute(eq(execution), anyString(), eq(1000L));
    }

    @Test
    void reOnSampleCase_includesErrorOutput() {
        TestCase tc = new TestCase("in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        SubmissionExecution execution = readyExecution();
        stubPrepare(execution);
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
                .thenReturn(new ExecutionResponse(ExecutionStatus.RUNTIME_ERROR, "", "Traceback...\nValueError: invalid literal", 1, 50));

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
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
                .thenReturn(new ExecutionResponse(ExecutionStatus.RUNTIME_ERROR, "", "some stderr the client must never see", 1, 50));

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
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
                .thenReturn(new ExecutionResponse(ExecutionStatus.ERROR, "", "failed to run docker: boom", -1, 50));

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
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
                .thenReturn(new ExecutionResponse(ExecutionStatus.SUCCESS, "wrong-output", "", 0, 50));

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
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
                .thenReturn(new ExecutionResponse(ExecutionStatus.TIMEOUT, "", "", -1, 2000));

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
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
                .thenReturn(new ExecutionResponse(ExecutionStatus.RUNTIME_ERROR, "", longStderr, 1, 50));

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
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
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
        when(executionService.execute(eq(execution), anyString(), eq(2000L)))
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
        return new ExecutionResponse(ExecutionStatus.SUCCESS, stdout, "", 0, executionTimeMs);
    }
}
