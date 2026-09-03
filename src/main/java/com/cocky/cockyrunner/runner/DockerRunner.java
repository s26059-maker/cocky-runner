package com.cocky.cockyrunner.runner;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
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

    /** Where the submission workspace is bind-mounted inside every container. */
    private static final String CONTAINER_WORK_DIR = "/work";

    /** Fixed timeout for the compile step, independent of any per-test-case run timeout. */
    static final long COMPILE_TIMEOUT_MS = 10_000L;

    /**
     * Upper bound on any run timeout accepted by this runner, caller-supplied or
     * computed. This is the single owner of that policy - {@link
     * com.cocky.cockyrunner.service.ExecutionService} references this constant
     * rather than duplicating it, and {@link
     * com.cocky.cockyrunner.repository.JsonProblemRepository} checks every
     * problem/language combination against it at startup (via {@link
     * com.cocky.cockyrunner.domain.LanguageSpec#resolvedTimeoutMs(int, long)}) so a
     * problem whose resolved timeout would exceed it is caught before it can ever
     * reach {@link #run}, rather than surfacing as a confusing 400 on first submit.
     */
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
            // Written once per submission, alongside the source - every run() call
            // below invokes this rather than the user's program directly, so it must
            // be in place before the first container for this workspace ever starts.
            RunnerScript.writeTo(workDir);
        } catch (IOException | UncheckedIOException e) {
            log.error("failed to prepare submission workspace", e);
            return DockerSubmissionExecution.infraFailure(this, null,
                    errorResponse("failed to prepare workspace: " + e.getMessage()));
        }

        if (spec.needsCompile()) {
            CompileOutcome outcome = compile(workDir, spec, code.length());
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
        return ExecutionResponse.withoutTiming(ExecutionStatus.ERROR, "", message, -1, 0);
    }

    /**
     * Runs one test case's stdin through the already-prepared workspace. Package-visible:
     * only {@link DockerSubmissionExecution} should call this, so every run goes through
     * a workspace that {@link #prepareSubmission(LanguageSpec, String)} set up first.
     */
    ExecutionResponse run(Path workDir, LanguageSpec spec, String stdin, long timeoutMs) {
        validateTimeout(timeoutMs);
        String containerName = "run-" + UUID.randomUUID();
        // Declared (with a real value) here rather than literally inside the try
        // below, only because a variable assigned inside a try block isn't visible
        // from its own catch block, and the catch needs to report *an* elapsed
        // time even when clearTiming() itself is what throws. nanoTime, not
        // currentTimeMillis: this measures elapsed duration, and the wall clock
        // can jump (NTP sync) in ways that would corrupt that.
        long startNanos = System.nanoTime();
        try {
            // Belt-and-suspenders: the wrapper script itself truncates this file as
            // its first action, but that only helps if the container starts at all -
            // if `docker run` itself never launches, a stale file from the previous
            // test case in this same workspace would otherwise be read as this
            // one's result. Inside the try (not before it, and no longer catching
            // its own IOException) so a failure here - e.g. a workspace file that
            // became undeletable - is reported as an ERROR response by the catch
            // below instead of propagating out of run() uncaught.
            RunnerScript.clearTiming(workDir);

            List<String> wrappedCommand = new ArrayList<>();
            wrappedCommand.add("sh");
            wrappedCommand.add(CONTAINER_WORK_DIR + "/" + RunnerScript.FILE_NAME);
            wrappedCommand.addAll(spec.runCommand());
            // Not read-only: the wrapper needs to write /work/.timing. The compile
            // step (below) still mounts read-write for its own reasons (writing the
            // compiled binary) and is untouched by this - it never runs the wrapper.
            List<String> command = buildContainerCommand(containerName, workDir, spec.dockerImage(), wrappedCommand, false);
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
                // Deliberately not parsed via TimingParser: on a real timeout the
                // wrapper wrote START/BEFORE and then never got to run AFTER/END (the
                // user program itself is what didn't finish), so the file exists but
                // is genuinely partial - parsing it would log a warning on every
                // single TLE. A timeout with no reliable user-time breakdown is
                // exactly the unmeasured case, same as no file at all.
                return ExecutionResponse.withoutTiming(ExecutionStatus.TIMEOUT, stdoutCollector.output(), stderrCollector.output(),
                        -1, elapsedMs(startNanos));
            }

            stdoutThread.join();
            stderrThread.join();

            int exitCode = process.exitValue();
            String stdout = stdoutCollector.output();
            String stderr = stderrCollector.output();
            ExecutionTiming timing = TimingParser.parse(workDir, elapsedMs(startNanos));

            if (exitCode == DOCKER_CLI_FAILURE_EXIT_CODE) {
                log.error("docker run failed for container {}: {}", containerName, stderr);
                return new ExecutionResponse(ExecutionStatus.ERROR, stdout, stderr, exitCode,
                        timing.totalWallMs(), timing.userWallMs(), timing.userCpuMs());
            }

            ExecutionStatus status = exitCode == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.RUNTIME_ERROR;
            return new ExecutionResponse(status, stdout, stderr, exitCode,
                    timing.totalWallMs(), timing.userWallMs(), timing.userCpuMs());

        } catch (IOException e) {
            // Covers both a docker process that never launched and clearTiming()
            // failing to delete a leftover .timing file - either way there's no run
            // to report on, and elapsedMs(startNanos) here is not meaningful (the
            // run itself may never have started) so it's not worth distinguishing
            // further.
            log.error("failed to prepare workspace or launch docker process", e);
            return ExecutionResponse.withoutTiming(ExecutionStatus.ERROR, "", "failed to run docker: " + e.getMessage(),
                    -1, elapsedMs(startNanos));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("execution was interrupted", e);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * Runs the language's compile command against the workspace with the same
     * resource constraints as an execution container, but a fixed timeout that is
     * independent of the problem's/language's run time limit.
     *
     * @param sourceLength length of the submitted source in characters, logged (not
     *                     the source itself) if compilation times out
     */
    private CompileOutcome compile(Path workDir, LanguageSpec spec, int sourceLength) {
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
                // warn, not info: a compile that doesn't finish in COMPILE_TIMEOUT_MS is
                // usually the user's code (e.g. a template metaprogramming blowup), but it's
                // also the one compile failure shape that infrastructure trouble (an
                // overloaded host, a stuck container) could produce, so it's worth being able
                // to spot a spike of these separately from ordinary nonzero-exit failures.
                // Source length only, never the source itself.
                log.warn("compilation timed out after {}ms for {} (source length {} chars)",
                        COMPILE_TIMEOUT_MS, spec.sourceFileName(), sourceLength);
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
        full.add(normalizeForDocker(workDir) + ":" + CONTAINER_WORK_DIR + (readOnlyMount ? ":ro" : ""));
        full.add("-w");
        full.add(CONTAINER_WORK_DIR);
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
