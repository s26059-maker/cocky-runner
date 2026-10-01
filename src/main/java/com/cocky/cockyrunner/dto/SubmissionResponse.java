package com.cocky.cockyrunner.dto;

import com.cocky.cockyrunner.domain.Verdict;

/**
 * @param maxExecutionTimeMs largest host-measured wall time (includes container
 *                           startup overhead) across the executed test cases
 * @param userWallMs         the largest user-program wall time across the executed
 *                           test cases; null if it couldn't be determined for any
 *                           executed case
 * @param userCpuMs          the largest user-program CPU time (user+sys) across the
 *                           executed test cases; null under the same conditions as
 *                           {@code userWallMs}
 */
public record SubmissionResponse(
        Verdict verdict,
        int passedCount,
        int totalCount,
        Integer failedCaseNumber,
        long maxExecutionTimeMs,
        String errorOutput,
        Long userWallMs,
        Long userCpuMs
) {
}
