package com.cocky.cockyrunner.runner;

/**
 * Breaks a single execution's measured wall time down into what the host
 * actually observed and what the user program itself accounted for, as
 * recovered by {@link TimingParser} from the wrapper script's {@code .timing}
 * file (see {@link RunnerScript}).
 *
 * @param totalWallMs wall time as measured on the host around the whole
 *                     {@code docker run} invocation - includes container
 *                     startup/teardown overhead (~425ms), always present
 * @param userWallMs   the user program's own wall time, measured inside the
 *                      container from just before it starts to just after it
 *                      exits; null if it couldn't be determined (see
 *                      {@link TimingParser})
 * @param userCpuMs     the user program's own CPU time (user+sys), measured
 *                      inside the container; null if it couldn't be determined
 */
public record ExecutionTiming(long totalWallMs, Long userWallMs, Long userCpuMs) {

    /**
     * An execution for which no in-container timing breakdown is available at
     * all - e.g. the {@code .timing} file never appeared, which is the normal
     * shape of a timeout (the wrapper never got to write it).
     */
    public static ExecutionTiming unmeasured(long totalWallMs) {
        return new ExecutionTiming(totalWallMs, null, null);
    }

    /**
     * The portion of {@link #totalWallMs} not accounted for by the user
     * program's own wall time - chiefly container startup/teardown - or null if
     * {@link #userWallMs} itself is null.
     */
    public Long overheadMs() {
        return userWallMs == null ? null : totalWallMs - userWallMs;
    }
}
