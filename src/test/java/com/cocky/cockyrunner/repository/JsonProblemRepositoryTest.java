package com.cocky.cockyrunner.repository;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.Problem;
import com.cocky.cockyrunner.domain.TestCase;
import com.cocky.cockyrunner.runner.DockerRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonProblemRepositoryTest {

    private static LanguageSpecRegistry testRegistry() {
        DockerProperties properties = new DockerProperties(
                Map.of("c", "gcc:14", "python", "python:3.11-slim"),
                5, "256m", 1.0, 64, 65536, "build/judge-work-test");
        return new LanguageSpecRegistry(properties);
    }

    private static Problem problemWithTimeLimit(int timeLimitMs) {
        TestCase tc = new TestCase("in", "out", true);
        return new Problem("p1", "title", "desc", timeLimitMs, List.of(tc));
    }

    @Test
    void throwsWhenAProblemLanguageCombinationExceedsMaxTimeout() {
        // PYTHON's multiplier is 3.0, so a base time limit just over
        // MAX_TIMEOUT_MS / 3 exceeds DockerRunner.MAX_TIMEOUT_MS for PYTHON, even
        // though the same problem would be fine for C (multiplier 1.0).
        int overLimitForPython = (int) (DockerRunner.MAX_TIMEOUT_MS / 3) + 1;
        Problem problem = problemWithTimeLimit(overLimitForPython);

        assertThatThrownBy(() -> JsonProblemRepository.validateTimeLimits(List.of(problem), testRegistry()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p1")
                .hasMessageContaining("PYTHON");
    }

    @Test
    void passesAtExactBoundaryForTheLargestMultiplier() {
        // Exactly at the boundary (resolved timeout == MAX_TIMEOUT_MS, not over it)
        // for PYTHON, the language with the largest multiplier - must not throw.
        int atBoundaryForPython = (int) (DockerRunner.MAX_TIMEOUT_MS / 3);
        Problem problem = problemWithTimeLimit(atBoundaryForPython);

        assertThatCode(() -> JsonProblemRepository.validateTimeLimits(List.of(problem), testRegistry()))
                .doesNotThrowAnyException();
    }

    @Test
    void passesForOrdinaryProblemTimeLimits() {
        Problem problem = problemWithTimeLimit(2000);

        assertThatCode(() -> JsonProblemRepository.validateTimeLimits(List.of(problem), testRegistry()))
                .doesNotThrowAnyException();
    }

    @Test
    void checksEveryProblemNotJustTheFirstOffender() {
        Problem ok = problemWithTimeLimit(2000);
        int overLimitForPython = (int) (DockerRunner.MAX_TIMEOUT_MS / 3) + 1;
        Problem tooLong = problemWithTimeLimit(overLimitForPython);

        assertThatThrownBy(() -> JsonProblemRepository.validateTimeLimits(List.of(ok, tooLong), testRegistry()))
                .isInstanceOf(IllegalStateException.class);
    }
}
