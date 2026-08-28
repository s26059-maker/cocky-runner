package com.cocky.cockyrunner.domain;

/**
 * Summary of a judged submission. Deliberately excludes test case input/expectedOutput
 * and execution stdout so hidden test case content can never leak through this type.
 *
 * {@code errorOutput} is the one intentional exception: for a {@link Verdict#RE}/
 * {@link Verdict#ERROR} result it carries the failing execution's stderr, but only
 * when the caller has already verified the failure happened on a public sample case
 * (see {@link com.cocky.cockyrunner.service.JudgeService}) - it must stay null in
 * every other case (hidden-case failures, WA, TLE) so no hidden content or stdout
 * leaks. For {@link Verdict#CE} it always carries the compiler's stderr regardless
 * of sample status: a compile error is about the submitted code itself, not about
 * any test case's data, so there is nothing hidden to leak.
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
