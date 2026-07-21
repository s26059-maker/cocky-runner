package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.JudgeResult;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.Problem;
import com.cocky.cockyrunner.domain.TestCase;
import com.cocky.cockyrunner.domain.Verdict;
import com.cocky.cockyrunner.dto.ExecutionRequest;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.ProblemNotFoundException;
import com.cocky.cockyrunner.repository.ProblemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JudgeServiceTest {

    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    private final ExecutionService executionService = mock(ExecutionService.class);
    private JudgeService judgeService;

    @BeforeEach
    void setUp() {
        judgeService = new JudgeService(problemRepository, executionService);
    }

    @Test
    void allCasesPass_returnsAc() {
        TestCase tc1 = new TestCase("in1", "out1", true);
        TestCase tc2 = new TestCase("in2", "out2", false);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc1, tc2));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        when(executionService.execute(any(), eq(2000L)))
                .thenReturn(success("out1", 100))
                .thenReturn(success("out2", 300));

        JudgeResult result = judgeService.judge("p1", Language.PYTHON, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.AC);
        assertThat(result.passedCount()).isEqualTo(2);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.failedCaseNumber()).isNull();
        assertThat(result.maxExecutionTimeMs()).isEqualTo(300);
    }

    @Test
    void secondCaseFails_returnsWaAndStopsBeforeThirdCase() {
        TestCase tc1 = new TestCase("in1", "out1", true);
        TestCase tc2 = new TestCase("in2", "expected2", false);
        TestCase tc3 = new TestCase("in3", "out3", false);
        Problem problem = new Problem("p1", "title", "desc", 2000, List.of(tc1, tc2, tc3));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));
        when(executionService.execute(any(), eq(2000L)))
                .thenReturn(success("out1", 100))
                .thenReturn(success("wrong-output", 150));

        JudgeResult result = judgeService.judge("p1", Language.PYTHON, "code");

        assertThat(result.verdict()).isEqualTo(Verdict.WA);
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.failedCaseNumber()).isEqualTo(2);
        verify(executionService, times(2)).execute(any(), eq(2000L));
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
        when(executionService.execute(any(), eq(2000L)))
                .thenReturn(new ExecutionResponse(status, "", "", exitCode, 500));

        JudgeResult result = judgeService.judge("p1", Language.PYTHON, "code");

        assertThat(result.verdict()).isEqualTo(expectedVerdict);
        assertThat(result.passedCount()).isEqualTo(0);
        assertThat(result.failedCaseNumber()).isEqualTo(1);
    }

    @Test
    void passesProblemTimeLimitAndStdinToExecute() {
        TestCase tc = new TestCase("stdin-in", "out", true);
        Problem problem = new Problem("p1", "title", "desc", 1234, List.of(tc));
        when(problemRepository.findById("p1")).thenReturn(Optional.of(problem));

        ArgumentCaptor<ExecutionRequest> requestCaptor = ArgumentCaptor.forClass(ExecutionRequest.class);
        ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        when(executionService.execute(requestCaptor.capture(), timeoutCaptor.capture()))
                .thenReturn(success("out", 50));

        judgeService.judge("p1", Language.PYTHON, "print(1)");

        assertThat(timeoutCaptor.getValue()).isEqualTo(1234L);
        assertThat(requestCaptor.getValue().language()).isEqualTo("PYTHON");
        assertThat(requestCaptor.getValue().code()).isEqualTo("print(1)");
        assertThat(requestCaptor.getValue().stdin()).isEqualTo("stdin-in");
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
