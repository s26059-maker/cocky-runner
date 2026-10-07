package com.cocky.cockyrunner.api;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageDockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import com.cocky.cockyrunner.runner.RunOutcome;
import com.cocky.cockyrunner.runner.SubmissionExecution;
import com.cocky.cockyrunner.service.ExecutionService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalExecutionServiceTest {

    private final ExecutionService executionService = mock(ExecutionService.class);
    private final SubmissionExecution execution = mock(SubmissionExecution.class);
    private ExternalExecutionService service;

    @BeforeEach
    void setUp() {
        service = newService(4);
        when(executionService.prepare(any(Language.class), anyString())).thenReturn(execution);
    }

    private ExternalExecutionService newService(int maxConcurrent) {
        DockerProperties docker = new DockerProperties(
                Map.of(
                        Language.C, new LanguageDockerProperties("gcc:14", 100),
                        Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 100),
                        Language.JAVA, new LanguageDockerProperties("eclipse-temurin:21-jdk", 100)),
                5, "256m", 1.0, 64, 65536, "build/judge-work-test");
        return new ExternalExecutionService(executionService, new LanguageSpecRegistry(docker),
                new RunnerApiProperties("t", maxConcurrent));
    }

    private static ExecutionResponse ok(String stdout, Long userWallMs) {
        return new ExecutionResponse(ExecutionStatus.SUCCESS, stdout, "", 0, 500, userWallMs, userWallMs);
    }

    private static RunOutcome outcome(ExecutionResponse response) {
        return new RunOutcome(response, false);
    }

    private void stubCases(RunOutcome... outcomes) {
        var stub = when(executionService.executeLimited(any(), anyString(), anyLong(), anyLong(), anyInt()));
        var chain = stub.thenReturn(outcomes[0]);
        for (int i = 1; i < outcomes.length; i++) {
            chain = chain.thenReturn(outcomes[i]);
        }
    }

    private static JudgeRequest judgeRequest(String... expectedOutputs) {
        List<JudgeRequest.TestCaseInput> cases = new java.util.ArrayList<>();
        for (String expected : expectedOutputs) {
            cases.add(new JudgeRequest.TestCaseInput("in", expected));
        }
        return new JudgeRequest("python", "print(1)", 1000, 262144L, cases);
    }

    // ---- verdict aggregation ----

    @Test
    void allCasesPass_returnsAc_withMaxOfUserWallTimes() {
        stubCases(outcome(ok("1\n", 30L)), outcome(ok("2\n", 120L)), outcome(ok("3\n", 80L)));

        JudgeApiResponse response = service.judge(judgeRequest("1", "2", "3"));

        assertThat(response.verdict()).isEqualTo(ApiVerdict.AC);
        assertThat(response.passedCount()).isEqualTo(3);
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.maxTimeMs()).isEqualTo(120);
        assertThat(response.maxMemoryKb()).isNull();
        assertThat(response.compileOutput()).isNull();
        verify(execution).close();
    }

    @Test
    void middleCaseWrongAnswer_stillRunsAllCases_andCountsPassed() {
        stubCases(outcome(ok("1\n", 10L)), outcome(ok("WRONG\n", 10L)), outcome(ok("3\n", 10L)));

        JudgeApiResponse response = service.judge(judgeRequest("1", "2", "3"));

        assertThat(response.verdict()).isEqualTo(ApiVerdict.WA);
        assertThat(response.passedCount()).isEqualTo(2);
        assertThat(response.totalCount()).isEqualTo(3);
        verify(executionService, times(3)).executeLimited(any(), anyString(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void verdictIsTheFirstFailingCasesVerdict() {
        ExecutionResponse tle = ExecutionResponse.withoutTiming(ExecutionStatus.TIMEOUT, "", "", -1, 2000);
        ExecutionResponse re = new ExecutionResponse(ExecutionStatus.RUNTIME_ERROR, "", "boom", 1, 500, 5L, 5L);
        stubCases(outcome(ok("1\n", 10L)), outcome(tle), outcome(ok("WRONG\n", 10L)), outcome(re));

        JudgeApiResponse response = service.judge(judgeRequest("1", "2", "3", "4"));

        assertThat(response.verdict()).isEqualTo(ApiVerdict.TLE);
        assertThat(response.passedCount()).isEqualTo(1);
        // the timed-out case has no user timing: it counts as the full limit (1000ms)
        assertThat(response.maxTimeMs()).isEqualTo(1000);
    }

    @Test
    void oomKilledRuntimeError_isMle_otherwiseRe() {
        ExecutionResponse killed = new ExecutionResponse(ExecutionStatus.RUNTIME_ERROR, "", "", 137, 500, 50L, 50L);
        stubCases(new RunOutcome(killed, true));
        assertThat(service.judge(judgeRequest("1")).verdict()).isEqualTo(ApiVerdict.MLE);

        stubCases(new RunOutcome(killed, false));
        assertThat(service.judge(judgeRequest("1")).verdict()).isEqualTo(ApiVerdict.RE);
    }

    @Test
    void compileError_stopsImmediately_withCompileOutput() {
        when(execution.compilationFailed()).thenReturn(true);
        when(execution.compileErrorOutput()).thenReturn("main.c:1: error");

        JudgeApiResponse response = service.judge(judgeRequest("1", "2"));

        assertThat(response.verdict()).isEqualTo(ApiVerdict.CE);
        assertThat(response.passedCount()).isZero();
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.compileOutput()).isEqualTo("main.c:1: error");
        verify(executionService, never()).executeLimited(any(), anyString(), anyLong(), anyLong(), anyInt());
        verify(execution).close();
    }

    @Test
    void infrastructureFailure_isAnErrorNotAVerdict() {
        when(execution.infrastructureFailed()).thenReturn(true);
        when(execution.infrastructureFailureDetail()).thenReturn("docker down");

        assertThatThrownBy(() -> service.judge(judgeRequest("1"))).isInstanceOf(RunnerInternalException.class);
        verify(execution).close();
    }

    @Test
    void errorStatusDuringACase_isAnErrorNotAVerdict() {
        stubCases(outcome(ExecutionResponse.withoutTiming(ExecutionStatus.ERROR, "", "docker", 125, 1)));

        assertThatThrownBy(() -> service.judge(judgeRequest("1"))).isInstanceOf(RunnerInternalException.class);
    }

    @Test
    void outputComparisonIgnoresTrailingWhitespaceAndBlankLines() {
        stubCases(outcome(ok("a  \r\nb\r\n\r\n\r\n", 1L)));

        assertThat(service.judge(judgeRequest("a\nb")).verdict()).isEqualTo(ApiVerdict.AC);
    }

    @Test
    void userWallTimeOverTheLimit_isTle_butMeasuredTimeIsKept() {
        // limit is 1000ms; 1400ms of user time is under the host timeout (limit + budget) but over the limit
        stubCases(outcome(ok("1\n", 1400L)));

        JudgeApiResponse response = service.judge(judgeRequest("1"));

        assertThat(response.verdict()).isEqualTo(ApiVerdict.TLE);
        assertThat(response.maxTimeMs()).isEqualTo(1400);
    }

    @Test
    void userWallTimeUnderOrAtTheLimit_passes() {
        stubCases(outcome(ok("1\n", 999L)));
        assertThat(service.judge(judgeRequest("1")).verdict()).isEqualTo(ApiVerdict.AC);

        stubCases(outcome(ok("1\n", 1000L)));
        assertThat(service.judge(judgeRequest("1")).verdict()).isEqualTo(ApiVerdict.AC);
    }

    @Test
    void run_userWallTimeOverTheLimit_isTleWithMeasuredTime() {
        stubCases(outcome(ok("1\n", 1400L)));
        RunApiResponse over = service.run(new RunRequest("python", "x", "", 1000, 262144L));
        assertThat(over.status()).isEqualTo(RunStatus.TLE);
        assertThat(over.timeMs()).isEqualTo(1400);

        stubCases(outcome(ok("1\n", 999L)));
        assertThat(service.run(new RunRequest("python", "x", "", 1000, 262144L)).status()).isEqualTo(RunStatus.OK);
    }

    @Test
    void truncatedStdout_isWa_evenIfThePrefixMatches() {
        stubCases(new RunOutcome(ok("1\n", 10L), false, true));

        JudgeApiResponse response = service.judge(judgeRequest("1"));

        assertThat(response.verdict()).isEqualTo(ApiVerdict.WA);
        assertThat(response.passedCount()).isZero();
    }

    @Test
    void judgeCapturesUpTo1Mb_runOnly64Kb() {
        stubCases(outcome(ok("1\n", 10L)));

        service.judge(judgeRequest("1"));
        verify(executionService).executeLimited(any(), anyString(), anyLong(), anyLong(), eq(1024 * 1024));

        service.run(new RunRequest("python", "x", "", 1000, 262144L));
        verify(executionService).executeLimited(any(), anyString(), anyLong(), anyLong(), eq(64 * 1024));
    }

    @Test
    void judgeComparesTheFullOutputNotThe64KbResponseCap() {
        String big = ("line\n").repeat(20_000); // 100KB
        stubCases(outcome(ok(big, 10L)));

        assertThat(service.judge(judgeRequest(big)).verdict()).isEqualTo(ApiVerdict.AC);
    }

    // ---- validation ----

    @Test
    void unsupportedOrUppercaseLanguage_isRejected() {
        for (String language : new String[] {"Python", "cpp", "", null}) {
            JudgeRequest request = new JudgeRequest(language, "x", 1000, 1024L,
                    List.of(new JudgeRequest.TestCaseInput("", "")));
            assertThatThrownBy(() -> service.judge(request)).isInstanceOf(InvalidExecutionRequestException.class);
        }
    }

    @Test
    void oversizedInputs_areRejected() {
        String big = "a".repeat(64 * 1024 + 1);
        assertThatThrownBy(() -> service.judge(new JudgeRequest("c", big, 1000, 1024L,
                List.of(new JudgeRequest.TestCaseInput("", "")))))
                .isInstanceOf(InvalidExecutionRequestException.class);
        assertThatThrownBy(() -> service.judge(new JudgeRequest("c", "x", 1000, 1024L,
                List.of(new JudgeRequest.TestCaseInput(big, "")))))
                .isInstanceOf(InvalidExecutionRequestException.class);
        assertThatThrownBy(() -> service.run(new RunRequest("c", "x", big, 1000, 1024L)))
                .isInstanceOf(InvalidExecutionRequestException.class);
    }

    @Test
    void exactly64KbIsAccepted() {
        stubCases(outcome(ok("", 1L)));
        String limit = "a".repeat(64 * 1024);
        assertThat(service.run(new RunRequest("python", "x", limit, 1000, 1024L)).status()).isEqualTo(RunStatus.OK);
    }

    @Test
    void multibyteInputIsMeasuredInBytes() {
        // 30,000 Korean chars = 90,000 UTF-8 bytes > 64KB, though only 30,000 chars
        String korean = "가".repeat(30_000);
        assertThatThrownBy(() -> service.run(new RunRequest("python", "x", korean, 1000, 1024L)))
                .isInstanceOf(InvalidExecutionRequestException.class);
    }

    @Test
    void emptyTestCasesAndNonPositiveLimits_areRejected() {
        assertThatThrownBy(() -> service.judge(new JudgeRequest("c", "x", 1000, 1024L, List.of())))
                .isInstanceOf(InvalidExecutionRequestException.class);
        assertThatThrownBy(() -> service.run(new RunRequest("c", "x", "", 0, 1024L)))
                .isInstanceOf(InvalidExecutionRequestException.class);
        assertThatThrownBy(() -> service.run(new RunRequest("c", "x", "", 1000, null)))
                .isInstanceOf(InvalidExecutionRequestException.class);
    }

    // ---- run ----

    @Test
    void run_ok_returnsStdoutAndUserTime() {
        stubCases(outcome(new ExecutionResponse(ExecutionStatus.SUCCESS, "hi\n", "warn", 0, 500, 42L, 40L)));

        RunApiResponse response = service.run(new RunRequest("python", "x", "", 1000, 262144L));

        assertThat(response.status()).isEqualTo(RunStatus.OK);
        assertThat(response.stdout()).isEqualTo("hi\n");
        assertThat(response.stderr()).isEqualTo("warn");
        assertThat(response.compileOutput()).isNull();
        assertThat(response.timeMs()).isEqualTo(42);
    }

    @Test
    void run_compileError() {
        when(execution.compilationFailed()).thenReturn(true);
        when(execution.compileErrorOutput()).thenReturn("syntax error");

        RunApiResponse response = service.run(new RunRequest("c", "x", "", 1000, 262144L));

        assertThat(response.status()).isEqualTo(RunStatus.CE);
        assertThat(response.compileOutput()).isEqualTo("syntax error");
        assertThat(response.stdout()).isEmpty();
    }

    @Test
    void run_timeoutAndMemory() {
        stubCases(outcome(ExecutionResponse.withoutTiming(ExecutionStatus.TIMEOUT, "partial", "", -1, 2000)));
        RunApiResponse tle = service.run(new RunRequest("python", "x", "", 1000, 262144L));
        assertThat(tle.status()).isEqualTo(RunStatus.TLE);
        assertThat(tle.timeMs()).isEqualTo(1000);

        stubCases(new RunOutcome(new ExecutionResponse(ExecutionStatus.RUNTIME_ERROR, "", "", 137, 500, 9L, 9L), true));
        assertThat(service.run(new RunRequest("python", "x", "", 1000, 262144L)).status()).isEqualTo(RunStatus.MLE);
    }

    @Test
    void run_capsStdoutAndStderrAt64Kb() {
        String huge = "x".repeat(70_000);
        stubCases(outcome(new ExecutionResponse(ExecutionStatus.SUCCESS, huge, huge, 0, 500, 1L, 1L)));

        RunApiResponse response = service.run(new RunRequest("python", "x", "", 1000, 262144L));

        assertThat(response.stdout()).hasSize(64 * 1024);
        assertThat(response.stderr()).hasSize(64 * 1024);
    }

    @Test
    void tinyMemoryLimitIsRaisedToDockersMinimum() {
        stubCases(outcome(ok("", 1L)));

        service.run(new RunRequest("python", "x", "", 1000, 100L));

        verify(executionService).executeLimited(any(), anyString(), anyLong(), eq(ExternalExecutionService.MIN_DOCKER_MEMORY_KB), anyInt());
    }

    // ---- concurrency ----

    @Test
    void rejectsWithBusyWhenNoPermitIsImmediatelyAvailable() throws Exception {
        service = newService(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(executionService.executeLimited(any(), anyString(), anyLong(), anyLong(), anyInt())).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return outcome(ok("", 1L));
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<RunApiResponse> first = pool.submit(() -> service.run(new RunRequest("python", "x", "", 1000, 1024L)));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.run(new RunRequest("python", "x", "", 1000, 1024L)))
                    .isInstanceOf(RunnerBusyException.class);

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(RunStatus.OK);

            // permit was released: a new request goes through
            assertThat(service.run(new RunRequest("python", "x", "", 1000, 1024L)).status()).isEqualTo(RunStatus.OK);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void permitIsReleasedWhenExecutionFails() {
        service = newService(1);
        when(executionService.executeLimited(any(), anyString(), anyLong(), anyLong(), anyInt()))
                .thenThrow(new IllegalStateException("boom"));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.run(new RunRequest("python", "x", "", 1000, 1024L)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
