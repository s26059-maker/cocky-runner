package com.cocky.cockyrunner.dto;

import com.cocky.cockyrunner.domain.Verdict;

/**
 * @param maxExecutionTimeMs host-measured wall time (includes container startup
 *                           overhead) of whichever test case took the longest
 * @param userWallMs         the user program's own wall time for that same test
 *                           case; null when it couldn't be determined
 * @param userCpuMs          the user program's own CPU time (user+sys) for that
 *                           same test case; null under the same conditions as
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
