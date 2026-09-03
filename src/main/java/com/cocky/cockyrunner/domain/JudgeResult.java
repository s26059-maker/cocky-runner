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
 *
 * @param maxExecutionTimeMs host-measured wall time (includes container startup
 *                           overhead) of whichever test case took the longest - 0
 *                           when no test case ran (CE, infra failure)
 * @param userWallMs         the user program's own wall time for that same
 *                           slowest test case, as recovered from its {@code
 *                           .timing} file (see {@link com.cocky.cockyrunner.runner.TimingParser});
 *                           null when it couldn't be determined (including when
 *                           no test case ran)
 * @param userCpuMs          the user program's own CPU time (user+sys) for that
 *                           same test case; null under the same conditions as
 *                           {@code userWallMs}
 */
public record JudgeResult(
        Verdict verdict,
        int passedCount,
        int totalCount,
        Integer failedCaseNumber,
        long maxExecutionTimeMs,
        String errorOutput,
        Long userWallMs,
        Long userCpuMs
) {

    /**
     * For a result where no test case ever ran at all - CE (compilation failed
     * before the test case loop) or an infrastructure failure (short-circuited
     * before the loop) - so there is no execution to have timed.
     */
    public static JudgeResult withoutTiming(Verdict verdict, int passedCount, int totalCount,
                                             Integer failedCaseNumber, long maxExecutionTimeMs, String errorOutput) {
        return new JudgeResult(verdict, passedCount, totalCount, failedCaseNumber, maxExecutionTimeMs, errorOutput, null, null);
    }
}
