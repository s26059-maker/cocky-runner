package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.dto.ExecutionRequest;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import com.cocky.cockyrunner.runner.DockerRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutionServiceTest {

    private final DockerRunner dockerRunner = mock(DockerRunner.class);
    private final DockerProperties properties = new DockerProperties(
            Map.of("python", "python:3.11-slim"), 5, "256m", 1.0, 64, 65536);
    private final ExecutionService executionService = new ExecutionService(dockerRunner, properties);

    @Test
    void usesGlobalTimeoutWhenNotSpecified() {
        ExecutionRequest request = new ExecutionRequest("python", "print(1)", "");
        ExecutionResponse expected = new ExecutionResponse(ExecutionStatus.SUCCESS, "1\n", "", 0, 10);
        when(dockerRunner.run(Language.PYTHON, request.code(), request.stdin(), 5000L)).thenReturn(expected);

        ExecutionResponse actual = executionService.execute(request);

        assertThat(actual).isEqualTo(expected);
        verify(dockerRunner).run(Language.PYTHON, request.code(), request.stdin(), 5000L);
    }

    @Test
    void usesCallerSuppliedTimeoutAtUpperBound() {
        ExecutionRequest request = new ExecutionRequest("python", "print(1)", "");
        ExecutionResponse expected = new ExecutionResponse(ExecutionStatus.SUCCESS, "1\n", "", 0, 10);
        when(dockerRunner.run(Language.PYTHON, request.code(), request.stdin(), 30_000L)).thenReturn(expected);

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
}