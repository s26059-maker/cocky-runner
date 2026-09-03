package com.cocky.cockyrunner.dto;

import com.cocky.cockyrunner.domain.ExecutionStatus;

/**
 * @param executionTimeMs host-measured wall time around the whole execution
 *                        (includes container startup overhead)
 * @param userWallMs      the user program's own wall time, as recovered from its
 *                        {@code .timing} file (see
 *                        {@link com.cocky.cockyrunner.runner.TimingParser}); null
 *                        when it couldn't be determined (including a timeout,
 *                        where the wrapper never finished writing it)
 * @param userCpuMs       the user program's own CPU time (user+sys); null under
 *                        the same conditions as {@code userWallMs}
 */
public record ExecutionResponse(
        ExecutionStatus status,
        String stdout,
        String stderr,
        int exitCode,
        long executionTimeMs,
        Long userWallMs,
        Long userCpuMs
) {

    /**
     * For a response where no timing breakdown was ever possible because no
     * container/run happened at all for it - not "the breakdown was attempted and
     * failed" (that case still goes through the canonical constructor with
     * {@code TimingParser}'s result, which may itself be null field-by-field).
     * Covers: infra failure before any container started, a docker process that
     * never launched, a compile error (no run() for this response), and a
     * timeout (a run did happen, but the wrapper's {@code .timing} is only ever
     * partial on a real TLE, so it's treated the same as "no breakdown" rather
     * than parsed).
     */
    public static ExecutionResponse withoutTiming(ExecutionStatus status, String stdout, String stderr,
                                                    int exitCode, long executionTimeMs) {
        return new ExecutionResponse(status, stdout, stderr, exitCode, executionTimeMs, null, null);
    }
}