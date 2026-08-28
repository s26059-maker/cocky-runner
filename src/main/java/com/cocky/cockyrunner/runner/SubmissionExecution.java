package com.cocky.cockyrunner.runner;

import com.cocky.cockyrunner.dto.ExecutionResponse;

/**
 * A prepared, ready-to-run submission: the source has been written to a
 * per-submission workspace and, for languages that need it, compiled once.
 * Callers run as many test cases as they like against the same workspace via
 * repeated {@link #run(String, long)} calls, then release it via {@link #close()}.
 *
 * <p>If the compile step failed with a nonzero exit code, {@link #compilationFailed()}
 * is true and {@link #run(String, long)} should not be called - the caller should
 * report a compile error using {@link #compileErrorOutput()} instead.
 *
 * <p>If something lower-level went wrong (the workspace couldn't be created, or the
 * compile container itself failed to launch), {@link #compilationFailed()} stays
 * false, {@link #infrastructureFailed()} is true, and every {@link #run(String, long)}
 * call simply returns the same fixed
 * {@link com.cocky.cockyrunner.domain.ExecutionStatus#ERROR} response every time it's
 * called. A caller that runs multiple test cases against one instance (i.e.
 * {@link com.cocky.cockyrunner.service.JudgeService}) should check
 * {@link #infrastructureFailed()} once, alongside {@link #compilationFailed()}, and
 * short-circuit instead of calling {@link #run(String, long)} once per test case -
 * otherwise one infrastructure failure is reported N times, which reads like the
 * user's code failed N times rather than like an internal error.
 */
public interface SubmissionExecution extends AutoCloseable {

    boolean compilationFailed();

    /**
     * Non-null only when {@link #compilationFailed()} is true; the compiler's
     * stderr (or a synthesized message on timeout).
     */
    String compileErrorOutput();

    /**
     * True when preparation failed below the level of the user's code (the
     * workspace couldn't be created, or the compile container itself couldn't
     * launch) rather than because of anything in the submission. Mutually
     * exclusive with {@link #compilationFailed()} - at most one of the two is true.
     */
    boolean infrastructureFailed();

    /**
     * Non-null only when {@link #infrastructureFailed()} is true; a message
     * describing what went wrong (e.g. "failed to run docker: ..."), meant for
     * server-side logging - callers should not expose it to the client the way
     * {@link #compileErrorOutput()} is exposed, since it's an internal failure
     * and not about the submitted code.
     */
    String infrastructureFailureDetail();

    ExecutionResponse run(String stdin, long timeoutMs);

    @Override
    void close();
}
