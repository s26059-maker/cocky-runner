package com.cocky.cockyrunner.repository;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageDockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.Problem;
import com.cocky.cockyrunner.domain.TestCase;
import com.cocky.cockyrunner.runner.DockerRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonProblemRepositoryTest {

    private static LanguageSpecRegistry registryWithBudgets(long budgetC, long budgetPython) {
        DockerProperties properties = new DockerProperties(
                Map.of(
                        Language.C, new LanguageDockerProperties("gcc:14", budgetC),
                        Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", budgetPython)
                ),
                5, "256m", 1.0, 64, 65536, "build/judge-work-test");
        return new LanguageSpecRegistry(properties);
    }

    // A small, uniform per-language budget used by tests below that are only about the
    // multiplier/boundary math, not about the budget itself - chosen (3) so that
    // MAX_TIMEOUT_MS - budget divides evenly by PYTHON's 3.0 multiplier, giving a clean
    // integer boundary with no rounding ambiguity.
    private static LanguageSpecRegistry uniformBudgetRegistry() {
        return registryWithBudgets(3, 3);
    }

    private static Problem problemWithTimeLimit(int timeLimitMs) {
        TestCase tc = new TestCase("in", "out", true);
        return new Problem("p1", "title", "desc", timeLimitMs, List.of(tc));
    }

    @Test
    void throwsWhenAProblemLanguageCombinationExceedsMaxTimeout() {
        // PYTHON's multiplier is 3.0, so a base time limit just over
        // (MAX_TIMEOUT_MS - budget) / 3 exceeds DockerRunner.MAX_TIMEOUT_MS for PYTHON,
        // even though the same problem would be fine for C (multiplier 1.0).
        LanguageSpecRegistry registry = uniformBudgetRegistry();
        int overLimitForPython = 10_000; // resolved = 10000*3 + 3 = 30003 > MAX_TIMEOUT_MS (30000)
        Problem problem = problemWithTimeLimit(overLimitForPython);

        assertThatThrownBy(() -> JsonProblemRepository.validateTimeLimits(List.of(problem), registry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p1")
                .hasMessageContaining("PYTHON");
    }

    @Test
    void passesAtExactBoundaryForTheLargestMultiplier() {
        // Exactly at the boundary (resolved timeout == MAX_TIMEOUT_MS, not over it)
        // for PYTHON, the language with the largest multiplier - must not throw.
        LanguageSpecRegistry registry = uniformBudgetRegistry();
        int atBoundaryForPython = 9_999; // resolved = 9999*3 + 3 = 30000 == MAX_TIMEOUT_MS
        Problem problem = problemWithTimeLimit(atBoundaryForPython);

        assertThatCode(() -> JsonProblemRepository.validateTimeLimits(List.of(problem), registry))
                .doesNotThrowAnyException();
    }

    @Test
    void passesForOrdinaryProblemTimeLimits() {
        Problem problem = problemWithTimeLimit(2000);

        assertThatCode(() -> JsonProblemRepository.validateTimeLimits(List.of(problem), uniformBudgetRegistry()))
                .doesNotThrowAnyException();
    }

    @Test
    void checksEveryProblemNotJustTheFirstOffender() {
        LanguageSpecRegistry registry = uniformBudgetRegistry();
        Problem ok = problemWithTimeLimit(2000);
        Problem tooLong = problemWithTimeLimit(10_000);

        assertThatThrownBy(() -> JsonProblemRepository.validateTimeLimits(List.of(ok, tooLong), registry))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- startupBudgetMs is folded into resolvedTimeoutMs BEFORE this check, not
    // after - these tests pin that order down directly, and that the budget used is
    // each language's own configured value (via LanguageSpecRegistry), not a shared one. --

    @Test
    void startupBudgetMs_isIncludedInTheMaxTimeoutCheck() {
        // At the boundary on the multiplier alone (would pass with budget 0) - but
        // PYTHON's configured budget of 1ms must push it over MAX_TIMEOUT_MS and be
        // caught, proving the budget is added before the check runs rather than after
        // (or not at all).
        LanguageSpecRegistry registry = registryWithBudgets(1, 1);
        int atBoundaryForPython = 10_000; // 10000*3 = 30000 == MAX with budget 0, but +1 exceeds it
        Problem problem = problemWithTimeLimit(atBoundaryForPython);

        assertThatThrownBy(() -> JsonProblemRepository.validateTimeLimits(List.of(problem), registry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PYTHON");
    }

    @Test
    void startupBudgetMs_stillPassesWhenWellUnderTheMax() {
        LanguageSpecRegistry registry = registryWithBudgets(1000, 1000);
        Problem problem = problemWithTimeLimit(2000);

        assertThatCode(() -> JsonProblemRepository.validateTimeLimits(List.of(problem), registry))
                .doesNotThrowAnyException();
    }

    @Test
    void aLanguageSpecificBudget_canPushOnlyThatLanguageOverTheMax() {
        // C gets a small budget that leaves it well under the max; PYTHON gets a huge
        // budget that alone (even with a modest base time limit) pushes it over -
        // proving each language's own configured budget is what's actually used, not
        // a single shared value.
        LanguageSpecRegistry registry = registryWithBudgets(1000, 29_000);
        Problem problem = problemWithTimeLimit(1000);
        // C:      1000*1.0 +  1000 =  2000ms, well under MAX_TIMEOUT_MS (30000)
        // PYTHON: 1000*3.0 + 29000 = 32000ms, over MAX_TIMEOUT_MS (30000)

        assertThatThrownBy(() -> JsonProblemRepository.validateTimeLimits(List.of(problem), registry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p1")
                .hasMessageContaining("PYTHON");
    }
}
