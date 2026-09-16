package com.cocky.cockyrunner.domain;

import java.util.List;

/**
 * All the per-language configuration the judge needs: which image to run, what
 * to name the source file, how to compile it (if at all), how to run it, and how
 * much slack to give it on the wall-clock time limit.
 *
 * <p>Defensively copied in the compact constructor - callers can pass a mutable
 * {@code List} (or {@code null} for {@code compileCommand}) without risking that
 * a later mutation on their end changes a spec that's shared (via
 * {@link com.cocky.cockyrunner.config.LanguageSpecRegistry}) across every
 * submission for that language.
 *
 * @param dockerImage          image used for both the compile and run containers
 * @param sourceFileName       name the submitted code is written to on disk, e.g. "main.c"
 * @param compileCommand       command run inside the compile container; may be passed as
 *                             null or empty for languages that need no compile step (e.g.
 *                             Python) - either way it's normalized to an empty list, so
 *                             {@link #compileCommand()} itself is never null
 * @param runCommand           command run inside the execution container
 * @param timeLimitMultiplier  multiplier applied to a problem's timeLimitMs for this language
 * @param startupBudgetMs      this language's fixed slack, in milliseconds, added on top of
 *                             the scaled time limit to account for container startup overhead
 *                             (see {@link #resolvedTimeoutMs(int)}) - configured per language
 *                             because startup cost varies by runtime (e.g. a JVM-based
 *                             language needs more than a native one)
 */
public record LanguageSpec(
        String dockerImage,
        String sourceFileName,
        List<String> compileCommand,
        List<String> runCommand,
        double timeLimitMultiplier,
        long startupBudgetMs
) {
    public LanguageSpec {
        compileCommand = compileCommand == null ? List.of() : List.copyOf(compileCommand);
        runCommand = List.copyOf(runCommand);
    }

    public boolean needsCompile() {
        return !compileCommand.isEmpty();
    }

    /**
     * The run timeout for this language on a problem with the given base time
     * limit: {@code problemTimeLimitMs * timeLimitMultiplier}, rounded to the
     * nearest millisecond, plus this language's own {@code startupBudgetMs} slack
     * for container startup overhead. The single formula both
     * {@link com.cocky.cockyrunner.service.JudgeService} (to compute the timeout
     * it actually runs with) and {@link com.cocky.cockyrunner.repository.JsonProblemRepository}
     * (to validate at startup that no problem/language combination would exceed
     * {@code DockerRunner.MAX_TIMEOUT_MS}) use, so the two can never drift apart.
     *
     * <p>{@code startupBudgetMs} is added here, inside the one formula both of
     * those callers share, specifically so it's included <em>before</em> either
     * of them checks the result against {@code DockerRunner.MAX_TIMEOUT_MS} -
     * were the budget added after that check instead, a problem/language
     * combination could pass validation right at the boundary and then still
     * exceed the real ceiling once the budget is folded in.
     *
     * <p>On policy: the host enforces {@code timeoutMs} as a single wall-clock
     * deadline around the whole container run ({@code process.waitFor} in
     * {@code DockerRunner.run}) - it has no way to separate "time spent starting
     * the container" from "time spent in the user's program" while the run is in
     * progress, so the two can't be budgeted independently. Padding the deadline
     * by a fixed {@code startupBudgetMs} is the only lever available, and it's a
     * blunt one: if the container happens to start faster than the budget
     * assumes, the unused slack silently becomes extra time for the user's
     * program too (a borderline-TLE submission can pass on a lucky fast start
     * and fail on a slow one, for the same code). That's accepted deliberately -
     * between the two ways this estimate can be wrong, a too-small budget means
     * wrongly failing correct submissions with TLE (an unrecoverable, confusing
     * false positive from the user's point of view), while a too-large budget
     * only costs a bit of throughput and a slightly softer time limit. The
     * policy is to err generous.
     */
    public long resolvedTimeoutMs(int problemTimeLimitMs) {
        return Math.round(problemTimeLimitMs * timeLimitMultiplier) + startupBudgetMs;
    }
}
