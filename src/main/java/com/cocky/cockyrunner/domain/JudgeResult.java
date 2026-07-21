package com.cocky.cockyrunner.domain;

/**
 * Summary of a judged submission. Deliberately excludes test case input/expectedOutput
 * and execution stdout/stderr so hidden test case content can never leak through this type.
 */
public record JudgeResult(
        Verdict verdict,
        int passedCount,
        int totalCount,
        Integer failedCaseNumber,
        long maxExecutionTimeMs
) {
}
