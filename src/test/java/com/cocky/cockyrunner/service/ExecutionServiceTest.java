package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.dto.ExecutionRequest;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import com.cocky.cockyrunner.runner.DockerRunner;
import com.cocky.cockyrunner.runner.SubmissionExecution;
import com.cocky.cockyrunner.util.TextTruncator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutionServiceTest {

    private final DockerRunner dockerRunner = mock(DockerRunner.class);
    private final DockerProperties properties = new DockerProperties(
            Map.of("c", "gcc:14", "python", "python:3.11-slim"), 5, "256m", 1.0, 64, 65536, "build/judge-work-test");
    private final LanguageSpecRegistry languageSpecRegistry = new LanguageSpecRegistry(properties);
    private final ExecutionService executionService = new ExecutionService(dockerRunner, languageSpecRegistry, properties);

    private SubmissionExecution readyExecution() {
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(false);
        when(execution.infrastructureFailed()).thenReturn(false);
        return execution;
    }

    @Test
    void usesGlobalTimeoutWhenNotSpecified() {
        ExecutionRequest request = new ExecutionRequest("python", "print(1)", "");
        SubmissionExecution execution = readyExecution();
        when(dockerRunner.prepareSubmission(eq(languageSpecRegistry.get(Language.PYTHON)), eq("print(1)")))
                .thenReturn(execution);
        ExecutionResponse expected = new ExecutionResponse(ExecutionStatus.SUCCESS, "1\n", "", 0, 10);
        when(execution.run("", 5000L)).thenReturn(expected);

        ExecutionResponse actual = executionService.execute(request);

        assertThat(actual).isEqualTo(expected);
        // Explicit interaction check, restored to match the original pre-rewrite test
        // (which verified dockerRunner.run(...) directly) now that the call is split
        // across prepareSubmission()/run() - the stub-matching above already implies
        // this indirectly, but this makes it an explicit, unambiguous assertion again.
        verify(dockerRunner).prepareSubmission(languageSpecRegistry.get(Language.PYTHON), "print(1)");
        verify(execution).run("", 5000L);
        verify(execution).close();
    }

    @Test
    void usesCallerSuppliedTimeoutAtUpperBound() {
        ExecutionRequest request = new ExecutionRequest("python", "print(1)", "");
        SubmissionExecution execution = readyExecution();
        when(dockerRunner.prepareSubmission(eq(languageSpecRegistry.get(Language.PYTHON)), eq("print(1)")))
                .thenReturn(execution);
        ExecutionResponse expected = new ExecutionResponse(ExecutionStatus.SUCCESS, "1\n", "", 0, 10);
        when(execution.run("", 30_000L)).thenReturn(expected);

        ExecutionResponse actual = executionService.execute(request, 30_000L);

        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 30_001, 60_000})
    void rejectsInvalidTimeout(long timeoutMs) {
        ExecutionRequest request = new ExecutionRequest("python", "print(1)", "");

        assertThatThrownBy(() -> executionService.execute(request, timeoutMs))
                .isInstanceOf(InvalidExecutionRequestException.class);

        verifyNoInteractions(dockerRunner);
    }

    @Test
    void compileFailure_mapsToCompileErrorStatusWithTruncatedStderr() {
        ExecutionRequest request = new ExecutionRequest("c", "bad code", "");
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(true);
        String longStderr = "x".repeat(TextTruncator.MAX_LENGTH + 100);
        when(execution.compileErrorOutput()).thenReturn(longStderr);
        when(dockerRunner.prepareSubmission(eq(languageSpecRegistry.get(Language.C)), eq("bad code")))
                .thenReturn(execution);

        ExecutionResponse response = executionService.execute(request);

        assertThat(response.status()).isEqualTo(ExecutionStatus.COMPILE_ERROR);
        assertThat(response.stderr()).hasSizeLessThan(longStderr.length());
        assertThat(response.stderr()).contains("truncated");
        verify(execution, never()).run(anyString(), anyLong());
        verify(execution).close();
    }

    @Test
    void prepare_rejectsBlankCode() {
        assertThatThrownBy(() -> executionService.prepare(Language.C, "   "))
                .isInstanceOf(InvalidExecutionRequestException.class);

        verifyNoInteractions(dockerRunner);
    }

    @Test
    void prepare_rejectsNullCode() {
        assertThatThrownBy(() -> executionService.prepare(Language.C, null))
                .isInstanceOf(InvalidExecutionRequestException.class);

        verifyNoInteractions(dockerRunner);
    }

    @Test
    void executeWithSubmissionExecution_rejectsCompilationFailed() {
        SubmissionExecution execution = mock(SubmissionExecution.class);
        when(execution.compilationFailed()).thenReturn(true);

        assertThatThrownBy(() -> executionService.execute(execution, "in", 1000L))
                .isInstanceOf(IllegalStateException.class);

        verify(execution, never()).run(any(), anyLong());
    }

    @Test
    void executeWithSubmissionExecution_delegatesToRun() {
        SubmissionExecution execution = readyExecution();
        ExecutionResponse expected = new ExecutionResponse(ExecutionStatus.SUCCESS, "out", "", 0, 10);
        when(execution.run("in", 1500L)).thenReturn(expected);

        ExecutionResponse actual = executionService.execute(execution, "in", 1500L);

        assertThat(actual).isEqualTo(expected);
    }
}
