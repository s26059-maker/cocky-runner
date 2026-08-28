package com.cocky.cockyrunner.runner;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs untrusted code inside throwaway Docker containers, using a single
 * per-submission workspace on the host (bind-mounted into every container for
 * that submission) so the same approach covers both compiled and
 * interpreted languages: {@link #prepareSubmission(LanguageSpec, String)}
 * creates the workspace and, if the language needs it, compiles once; the
 * returned {@link SubmissionExecution} then runs each test case against that
 * same workspace.
 *
 * <p>The exit code convention here follows the Docker CLI: 125 means the
 * `docker run` invocation itself failed (daemon down, bad flags, ...), which is
 * reported as ERROR rather than as a RUNTIME_ERROR/CE coming from the user's code.
 */
@Component
public class DockerRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerRunner.class);
    private static final int DOCKER_CLI_FAILURE_EXIT_CODE = 125;

    /** Fixed timeout for the compile step, independent of any per-test-case run timeout. */
    static final long COMPILE_TIMEOUT_MS = 10_000L;

    /** Upper bound on any run timeout accepted by this runner, caller-supplied or computed. */
    public static final long MAX_TIMEOUT_MS = 30_000L;

    private final DockerProperties properties;
    private final Path workDirRoot;

    public DockerRunner(DockerProperties properties) {
        this.properties = properties;
        this.workDirRoot = resolveWorkDirRoot(properties.workDir());
        try {
            Files.createDirectories(workDirRoot);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "failed to create runner.docker.work-dir at " + workDirRoot, e);
        }
        log.info("submission workspaces will be created under {}", workDirRoot);
    }

    /**
     * Resolves the configured work-dir to an absolute path. A relative path (the
     * common case, e.g. the default {@code build/judge-work}) is resolved against
     * the JVM's working directory, since that's what a relative Docker bind-mount
     * source needs to be turned into an unambiguous absolute path anyway.
     */
    private static Path resolveWorkDirRoot(String configuredWorkDir) {
        Path path = Path.of(configuredWorkDir);
        return (path.isAbsolute() ? path : path.toAbsolutePath()).normalize();
    }

    /**
     * Creates a per-submission workspace, writes the source into it and, if the
     * language requires it, compiles it. Always returns a usable
     * {@link SubmissionExecution} - infrastructure failures (workspace couldn't be
     * created, compile container couldn't even launch) are folded into the
     * execution's {@code run()} results as {@link ExecutionStatus#ERROR} rather
     * than thrown, so callers have one place (the returned object) to inspect the
     * outcome.
     */
    public SubmissionExecution prepareSubmission(LanguageSpec spec, String code) {
        Path workDir;
        try {
            workDir = Files.createDirectory(workDirRoot.resolve(UUID.randomUUID().toString()));
            Files.writeString(workDir.resolve(spec.sourceFileName()), code, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("failed to prepare submission workspace", e);
            return DockerSubmissionExecution.infraFailure(this, null,
                    errorResponse("failed to prepare workspace: " + e.getMessage()));
        }

        if (spec.needsCompile()) {
            CompileOutcome outcome = compile(workDir, spec);
            switch (outcome.kind()) {
                case COMPILE_ERROR -> {
                    return DockerSubmissionExecution.compileFailure(this, workDir, outcome.message());
                }
                case INFRA_ERROR -> {
                    return DockerSubmissionExecution.infraFailure(this, workDir, errorResponse(outcome.message()));
                }
                case SUCCESS -> {
                    // fall through to a ready-to-run execution below
                }
            }
        }
        return DockerSubmissionExecution.ready(this, workDir, spec);
    }

    private ExecutionResponse errorResponse(String message) {
        return new ExecutionResponse(ExecutionStatus.ERROR, "", message, -1, 0);
    }

    /**
     * Runs one test case's stdin through the already-prepared workspace. Package-visible:
     * only {@link DockerSubmissionExecution} should call this, so every run goes through
     * a workspace that {@link #prepareSubmission(LanguageSpec, String)} set up first.
     */
    ExecutionResponse run(Path workDir, LanguageSpec spec, String stdin, long timeoutMs) {
        validateTimeout(timeoutMs);
        long start = System.currentTimeMillis();
        String containerName = "run-" + UUID.randomUUID();
        try {
            List<String> command = buildContainerCommand(containerName, workDir, spec.dockerImage(), spec.runCommand(), true);
            Process process = new ProcessBuilder(command).start();

            writeStdin(process, stdin);

            OutputCollector stdoutCollector = new OutputCollector(process.getInputStream(), properties.maxOutputBytes());
            OutputCollector stderrCollector = new OutputCollector(process.getErrorStream(), properties.maxOutputBytes());
            Thread stdoutThread = startDrainThread(stdoutCollector, "docker-stdout-" + containerName);
            Thread stderrThread = startDrainThread(stderrCollector, "docker-stderr-" + containerName);

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                killContainer(containerName);
                stdoutThread.join(TimeUnit.SECONDS.toMillis(5));
                stderrThread.join(TimeUnit.SECONDS.toMillis(5));
                return new ExecutionResponse(ExecutionStatus.TIMEOUT, stdoutCollector.output(), stderrCollector.output(),
                        -1, System.currentTimeMillis() - start);
            }

            stdoutThread.join();
            stderrThread.join();

            int exitCode = process.exitValue();
            String stdout = stdoutCollector.output();
            String stderr = stderrCollector.output();
            long elapsed = System.currentTimeMillis() - start;

            if (exitCode == DOCKER_CLI_FAILURE_EXIT_CODE) {
                log.error("docker run failed for container {}: {}", containerName, stderr);
                return new ExecutionResponse(ExecutionStatus.ERROR, stdout, stderr, exitCode, elapsed);
            }

            ExecutionStatus status = exitCode == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.RUNTIME_ERROR;
            return new ExecutionResponse(status, stdout, stderr, exitCode, elapsed);

        } catch (IOException e) {
            log.error("failed to launch docker process", e);
            return new ExecutionResponse(ExecutionStatus.ERROR, "", "failed to run docker: " + e.getMessage(),
                    -1, System.currentTimeMillis() - start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("execution was interrupted", e);
        }
    }

    /**
     * Runs the language's compile command against the workspace with the same
     * resource constraints as an execution container, but a fixed timeout that is
     * independent of the problem's/language's run time limit.
     */
    private CompileOutcome compile(Path workDir, LanguageSpec spec) {
        String containerName = "compile-" + UUID.randomUUID();
        try {
            List<String> command = buildContainerCommand(containerName, workDir, spec.dockerImage(), spec.compileCommand(), false);
            Process process = new ProcessBuilder(command).start();

            writeStdin(process, null);

            OutputCollector stdoutCollector = new OutputCollector(process.getInputStream(), properties.maxOutputBytes());
            OutputCollector stderrCollector = new OutputCollector(process.getErrorStream(), properties.maxOutputBytes());
            Thread stdoutThread = startDrainThread(stdoutCollector, "docker-compile-stdout-" + containerName);
            Thread stderrThread = startDrainThread(stderrCollector, "docker-compile-stderr-" + containerName);

            boolean finished = process.waitFor(COMPILE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                killContainer(containerName);
                stdoutThread.join(TimeUnit.SECONDS.toMillis(5));
                stderrThread.join(TimeUnit.SECONDS.toMillis(5));
                return CompileOutcome.compileError("compilation timed out after " + COMPILE_TIMEOUT_MS + "ms");
            }

            stdoutThread.join();
            stderrThread.join();

            int exitCode = process.exitValue();
            String stderr = stderrCollector.output();

            if (exitCode == DOCKER_CLI_FAILURE_EXIT_CODE) {
                log.error("docker run failed for compile container {}: {}", containerName, stderr);
                return CompileOutcome.infraError(stderr);
            }
            if (exitCode == 0) {
                return CompileOutcome.success();
            }
            log.info("compilation failed for container {} (exit {})", containerName, exitCode);
            return CompileOutcome.compileError(stderr.isBlank() ? "compilation failed with exit code " + exitCode : stderr);

        } catch (IOException e) {
            log.error("failed to launch compile container", e);
            return CompileOutcome.infraError("failed to run compiler: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("compilation was interrupted", e);
        }
    }

    private void validateTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new InvalidExecutionRequestException("timeoutMs must be positive: " + timeoutMs);
        }
        if (timeoutMs > MAX_TIMEOUT_MS) {
            throw new InvalidExecutionRequestException("timeoutMs must not exceed " + MAX_TIMEOUT_MS + ": " + timeoutMs);
        }
    }

    private void writeStdin(Process process, String stdin) throws IOException {
        try (OutputStream stdinStream = process.getOutputStream()) {
            if (stdin != null && !stdin.isEmpty()) {
                stdinStream.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private Thread startDrainThread(OutputCollector collector, String threadName) {
        Thread thread = new Thread(collector, threadName);
        thread.start();
        return thread;
    }

    private List<String> buildContainerCommand(String containerName, Path workDir, String image, List<String> command,
                                                 boolean readOnlyMount) {
        List<String> full = new ArrayList<>();
        full.add("docker");
        full.add("run");
        full.add("--rm");
        full.add("-i");
        full.add("--network=none");
        full.add("--memory=" + properties.memory());
        full.add("--cpus=" + properties.cpus());
        full.add("--pids-limit=" + properties.pidsLimit());
        full.add("--name");
        full.add(containerName);
        full.add("-v");
        full.add(normalizeForDocker(workDir) + ":/work" + (readOnlyMount ? ":ro" : ""));
        full.add("-w");
        full.add("/work");
        full.add(image);
        full.addAll(command);
        return full;
    }

    /**
     * Converts a host path into the form the Windows Docker CLI reliably parses
     * for bind mounts. {@code Path.toAbsolutePath().toString()} on Windows yields
     * backslashes (e.g. {@code C:\Users\...\tmp}); Docker Desktop generally accepts
     * that too, but forward slashes (e.g. {@code C:/Users/...}) are the form that's
     * unambiguous for the {@code host:container[:opts]} bind-mount syntax, since a
     * backslash-form path's drive-letter colon otherwise sits right next to the
     * separator colon. Verified against a real Docker Desktop instance: both forms
     * happened to work here, but this normalization removes the ambiguity rather
     * than relying on that.
     */
    private String normalizeForDocker(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private void killContainer(String containerName) {
        try {
            new ProcessBuilder("docker", "kill", containerName).start().waitFor(5, TimeUnit.SECONDS);
        } catch (IOException e) {
            log.warn("failed to kill container {}", containerName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("interrupted while killing container {}", containerName, e);
        }
    }

    /**
     * Recursively deletes a submission's workspace. Package-visible: called by
     * {@link DockerSubmissionExecution#close()} once all test cases for a
     * submission have run. Never throws - a cleanup failure must not affect the
     * grading result already produced.
     */
    void cleanup(Path workDir) {
        if (workDir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn("failed to delete {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("failed to clean up workspace {}", workDir, e);
        }
    }

    private record CompileOutcome(Kind kind, String message) {
        enum Kind { SUCCESS, COMPILE_ERROR, INFRA_ERROR }

        static CompileOutcome success() {
            return new CompileOutcome(Kind.SUCCESS, null);
        }

        static CompileOutcome compileError(String message) {
            return new CompileOutcome(Kind.COMPILE_ERROR, message);
        }

        static CompileOutcome infraError(String message) {
            return new CompileOutcome(Kind.INFRA_ERROR, message);
        }
    }
}
