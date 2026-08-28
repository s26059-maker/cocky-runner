package com.cocky.cockyrunner.runner;

import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import java.nio.file.Path;

/**
 * {@link DockerRunner}'s implementation of {@link SubmissionExecution}. Instances
 * are only ever created by {@link DockerRunner#prepareSubmission(LanguageSpec, String)},
 * via the three factory methods below, one per outcome of that call:
 *
 * <ul>
 *   <li>{@link #ready}: the workspace was created and (if needed) compiled
 *       successfully - {@link #run} delegates to {@link DockerRunner#run}.</li>
 *   <li>{@link #compileFailure}: the workspace exists but the user's code didn't
 *       compile - {@link #compilationFailed()} is true and {@link #run} must not
 *       be called (it throws if it is).</li>
 *   <li>{@link #infraFailure}: something below the user's code failed (workspace
 *       couldn't be created, or the compile container itself couldn't launch) -
 *       {@link #compilationFailed()} stays false and every {@link #run} call
 *       simply replays the same fixed {@link ExecutionResponse#status()} ERROR
 *       response, regardless of the workspace even existing.</li>
 * </ul>
 *
 * <p>{@link #close()} always delegates to {@link DockerRunner#cleanup(Path)}, which
 * is a no-op for a null workDir and never throws - so it's safe to close an instance
 * from any of the three factories, whether or not a workspace was ever created.
 */
final class DockerSubmissionExecution implements SubmissionExecution {

    private final DockerRunner runner;
    private final Path workDir;
    private final LanguageSpec spec;
    private final boolean compilationFailed;
    private final String compileErrorOutput;
    private final ExecutionResponse infraFailureResponse;

    private DockerSubmissionExecution(DockerRunner runner, Path workDir, LanguageSpec spec,
                                       boolean compilationFailed, String compileErrorOutput,
                                       ExecutionResponse infraFailureResponse) {
        this.runner = runner;
        this.workDir = workDir;
        this.spec = spec;
        this.compilationFailed = compilationFailed;
        this.compileErrorOutput = compileErrorOutput;
        this.infraFailureResponse = infraFailureResponse;
    }

    static DockerSubmissionExecution ready(DockerRunner runner, Path workDir, LanguageSpec spec) {
        return new DockerSubmissionExecution(runner, workDir, spec, false, null, null);
    }

    static DockerSubmissionExecution compileFailure(DockerRunner runner, Path workDir, String compileErrorOutput) {
        return new DockerSubmissionExecution(runner, workDir, null, true, compileErrorOutput, null);
    }

    static DockerSubmissionExecution infraFailure(DockerRunner runner, Path workDir, ExecutionResponse errorResponse) {
        return new DockerSubmissionExecution(runner, workDir, null, false, null, errorResponse);
    }

    @Override
    public boolean compilationFailed() {
        return compilationFailed;
    }

    @Override
    public String compileErrorOutput() {
        return compileErrorOutput;
    }

    @Override
    public boolean infrastructureFailed() {
        return infraFailureResponse != null;
    }

    @Override
    public String infrastructureFailureDetail() {
        return infraFailureResponse == null ? null : infraFailureResponse.stderr();
    }

    @Override
    public ExecutionResponse run(String stdin, long timeoutMs) {
        if (infraFailureResponse != null) {
            return infraFailureResponse;
        }
        if (compilationFailed) {
            throw new IllegalStateException("cannot run a test case: compilation failed for this submission");
        }
        return runner.run(workDir, spec, stdin, timeoutMs);
    }

    @Override
    public void close() {
        runner.cleanup(workDir);
    }
}
