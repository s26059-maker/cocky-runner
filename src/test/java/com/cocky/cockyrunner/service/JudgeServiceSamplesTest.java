package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageDockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.diagnostic.Diagnostic;
import com.cocky.cockyrunner.diagnostic.Severity;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.Problem;
import com.cocky.cockyrunner.domain.SampleRunResult;
import com.cocky.cockyrunner.domain.TestCase;
import com.cocky.cockyrunner.domain.Verdict;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.ProblemNotFoundException;
import com.cocky.cockyrunner.repository.ProblemRepository;
import com.cocky.cockyrunner.runner.SubmissionExecution;
import com.cocky.cockyrunner.util.TextTruncator;
import org.junit.jupiter.api.Test;

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

class JudgeServiceSamplesTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ExecutionService executionService = mock(ExecutionService.class);
    private final JudgeService judgeService = new JudgeService(problemRepository, executionService,
            new LanguageSpecRegistry(new DockerProperties(
                    Map.of(
                            Language.C, new LanguageDockerProperties("gcc:14", 100),
                            Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 100),
                            Language.JAVA, new LanguageDockerProperties("eclipse-temurin:21-jdk", 100)
                    ),
                    5, "256m", 1.0, 64, 65536, "build/judge-work-test")));

    private SubmissionExecution readyExecution() {
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(false);
        when(execution.infrastructureFailed()).thenReturn(false);
        when(executionService.prepare(any(Language.class), anyString())).thenReturn(execution);
        return execution;
    }

    private void stubProblem(TestCase... cases) {
        when(problemRepository.findById("p1"))
                .thenReturn(Optional.of(new Problem("p1", "title", "desc", 2000, List.of(cases))));
    }

    private static ExecutionResponse success(String stdout, Long userWallMs) {
        return new ExecutionResponse(ExecutionStatus.SUCCESS, stdout, "", 0, 300, userWallMs, null);
    }

    @Test
    void allSamplesPass_runsOnlySamplesAndReportsEachCase() {
        stubProblem(new TestCase("in1", "out1", true), new TestCase("hidden", "x", false),
                new TestCase("in2", "out2\n", true));
        SubmissionExecution execution = readyExecution();
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out1", 12L))
                .thenReturn(success("out2", 34L));

        SampleRunResult result = judgeService.judgeSamples("p1", Language.C, "code");

        assertThat(result.compileError()).isNull();
        assertThat(result.cases()).hasSize(2);
        assertThat(result.cases()).extracting(SampleRunResult.SampleCaseResult::verdict)
                .containsExactly(Verdict.AC, Verdict.AC);
        assertThat(result.cases()).extracting(SampleRunResult.SampleCaseResult::index).containsExactly(1, 2);
        assertThat(result.cases().get(0).input()).isEqualTo("in1");
        assertThat(result.cases().get(1).expectedOutput()).isEqualTo("out2\n");
        assertThat(result.cases().get(1).actualOutput()).isEqualTo("out2");
        assertThat(result.cases().get(0).userWallMs()).isEqualTo(12L);
        verify(executionService, never()).execute(eq(execution), eq("hidden"), anyLong());
        verify(execution).close();
    }

    @Test
    void someSamplesFail_keepsRunningAfterFirstFailure() {
        stubProblem(new TestCase("in1", "out1", true), new TestCase("in2", "out2", true),
                new TestCase("in3", "out3", true));
        SubmissionExecution execution = readyExecution();
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("wrong", 5L))
                .thenReturn(success("out2", 6L))
                .thenReturn(new ExecutionResponse(ExecutionStatus.RUNTIME_ERROR, "", "boom", 1, 100, 7L, null));

        SampleRunResult result = judgeService.judgeSamples("p1", Language.C, "code");

        assertThat(result.cases()).extracting(SampleRunResult.SampleCaseResult::verdict)
                .containsExactly(Verdict.WA, Verdict.AC, Verdict.RE);
        assertThat(result.cases().get(0).stderr()).isNull();
        assertThat(result.cases().get(2).stderr()).isEqualTo("boom");
        verify(executionService, times(3)).execute(eq(execution), anyString(), anyLong());
    }

    @Test
    void outputComparisonUsesJudgeNormalization_trailingWhitespaceAndCrlfIgnored() {
        stubProblem(new TestCase("in", "a b\nc", true));
        SubmissionExecution execution = readyExecution();
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("a b  \r\nc\r\n\r\n", null));

        SampleRunResult result = judgeService.judgeSamples("p1", Language.C, "code");

        assertThat(result.cases().get(0).verdict()).isEqualTo(Verdict.AC);
        assertThat(result.cases().get(0).userWallMs()).isNull();
    }

    @Test
    void longActualOutputAndStderrAreTruncated() {
        stubProblem(new TestCase("in", "out", true));
        SubmissionExecution execution = readyExecution();
        String huge = "x".repeat(TextTruncator.MAX_LENGTH + 100);
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(new ExecutionResponse(ExecutionStatus.RUNTIME_ERROR, huge, huge, 1, 100, null, null));

        SampleRunResult.SampleCaseResult only = judgeService.judgeSamples("p1", Language.C, "code").cases().get(0);

        assertThat(only.actualOutput()).isEqualTo(TextTruncator.truncate(huge)).contains("truncated");
        assertThat(only.stderr()).isEqualTo(TextTruncator.truncate(huge));
    }

    @Test
    void compileError_returnsMessageAndNoCases() {
        stubProblem(new TestCase("in", "out", true));
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(true);
        when(execution.compileErrorOutput()).thenReturn("main.c:1:1: error: expected ';'");
        when(executionService.prepare(any(Language.class), anyString())).thenReturn(execution);

        SampleRunResult result = judgeService.judgeSamples("p1", Language.C, "int main() {");

        assertThat(result.compileError()).isEqualTo("main.c:1:1: error: expected ';'");
        assertThat(result.cases()).isEmpty();
        verify(executionService, never()).execute(any(SubmissionExecution.class), anyString(), anyLong());
        verify(execution).close();
    }

    @Test
    void noSamples_returnsEmptyCasesWithoutPreparingAnything() {
        stubProblem(new TestCase("hidden", "out", false));

        SampleRunResult result = judgeService.judgeSamples("p1", Language.C, "code");

        assertThat(result.compileError()).isNull();
        assertThat(result.cases()).isEmpty();
        verifyNoInteractions(executionService);
    }

    @Test
    void infrastructureFailure_marksEverySampleError() {
        stubProblem(new TestCase("in1", "out1", true), new TestCase("in2", "out2", true));
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(false);
        when(execution.infrastructureFailed()).thenReturn(true);
        when(executionService.prepare(any(Language.class), anyString())).thenReturn(execution);

        SampleRunResult result = judgeService.judgeSamples("p1", Language.C, "code");

        assertThat(result.cases()).extracting(SampleRunResult.SampleCaseResult::verdict)
                .containsExactly(Verdict.ERROR, Verdict.ERROR);
        assertThat(result.cases()).allMatch(c -> c.actualOutput() == null && c.stderr() == null);
        verify(executionService, never()).execute(any(SubmissionExecution.class), anyString(), anyLong());
        verify(execution).close();
    }

    @Test
    void unknownProblem_throwsNotFound() {
        when(problemRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> judgeService.judgeSamples("nope", Language.C, "code"))
                .isInstanceOf(ProblemNotFoundException.class);
    }

    @Test
    void compileError_hasDiagnosticsAndScrubbedMessage() {
        stubProblem(new TestCase("in", "out", true));
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(true);
        when(execution.compileErrorOutput()).thenReturn("Main.java:4: error: ';' expected");
        when(executionService.prepare(any(Language.class), anyString())).thenReturn(execution);

        SampleRunResult result = judgeService.judgeSamples("p1", Language.JAVA, "code");

        assertThat(result.diagnostics()).containsExactly(new Diagnostic(4, null, Severity.ERROR, "';' expected"));
    }

    @Test
    void firstRuntimeErrorCase_providesDiagnosticsAndStderrIsScrubbed() {
        stubProblem(new TestCase("in1", "out1", true), new TestCase("in2", "out2", true),
                new TestCase("in3", "out3", true));
        SubmissionExecution execution = readyExecution();
        String first = "Traceback (most recent call last):\n  File \"/work/main.py\", line 3, in <module>\nKeyError: 'a'\n";
        String second = "Traceback (most recent call last):\n  File \"/work/main.py\", line 8, in <module>\nOSError: x\n";
        when(executionService.execute(eq(execution), anyString(), anyLong()))
                .thenReturn(success("out1", 5L))
                .thenReturn(new ExecutionResponse(ExecutionStatus.RUNTIME_ERROR, "", first, 1, 100, 7L, null))
                .thenReturn(new ExecutionResponse(ExecutionStatus.RUNTIME_ERROR, "", second, 1, 100, 7L, null));

        SampleRunResult result = judgeService.judgeSamples("p1", Language.PYTHON, "code");

        assertThat(result.diagnostics()).containsExactly(new Diagnostic(3, null, Severity.ERROR, "KeyError: 'a'"));
        assertThat(result.cases().get(1).stderr()).doesNotContain("/work/").contains("\"main.py\", line 3");
    }

    @Test
    void noFailures_hasNoDiagnostics() {
        stubProblem(new TestCase("in", "out", true));
        SubmissionExecution execution = readyExecution();
        when(executionService.execute(eq(execution), anyString(), anyLong())).thenReturn(success("out", 1L));

        assertThat(judgeService.judgeSamples("p1", Language.C, "code").diagnostics()).isEmpty();
    }
}
