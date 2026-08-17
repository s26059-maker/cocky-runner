package com.cocky.cockyrunner.domain;

/**
 * Summary of a judged submission. Deliberately excludes test case input/expectedOutput
 * and execution stdout so hidden test case content can never leak through this type.
 *
 * {@code errorOutput} is the one intentional exception: it carries the failing
 * execution's stderr, but only when the caller has already verified the failure
 * happened on a public sample case with verdict RE/ERROR (see
 * {@link com.cocky.cockyrunner.service.JudgeService}) - it must stay null in every
 * other case (hidden-case failures, WA, TLE) so no hidden content or stdout leaks.
 */
public record JudgeResult(
        Verdict verdict,
        int passedCount,
        int totalCount,
        Integer failedCaseNumber,
        long maxExecutionTimeMs,
        String errorOutput
) {
}
