package com.cocky.cockyrunner.runner;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.Language;
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
 * Runs untrusted code inside a throwaway Docker container.
 * The exit code convention here follows the Docker CLI: 125 means the
 * `docker run` invocation itself failed (daemon down, bad flags, ...), which is
 * reported as ERROR rather than as a RUNTIME_ERROR coming from the user's code.
 */
@Component
public class DockerRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerRunner.class);
    private static final int DOCKER_CLI_FAILURE_EXIT_CODE = 125;

    private final DockerProperties properties;

    public DockerRunner(DockerProperties properties) {
        this.properties = properties;
    }

    public ExecutionResponse run(Language language, String code, String stdin) {
        String image = properties.images().get(language.name().toLowerCase());
        if (image == null) {
            throw new InvalidExecutionRequestException("no docker image configured for language: " + language);
        }

        long start = System.currentTimeMillis();
        Path tempDir = null;
        String containerName = "exec-" + UUID.randomUUID();
        try {
            tempDir = Files.createTempDirectory("cocky-runner-");
            Files.writeString(tempDir.resolve(language.fileName()), code, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(buildCommand(containerName, tempDir, image, language));
            Process process = pb.start();

            writeStdin(process, stdin);

            OutputCollector stdoutCollector = new OutputCollector(process.getInputStream(), properties.maxOutputBytes());
            OutputCollector stderrCollector = new OutputCollector(process.getErrorStream(), properties.maxOutputBytes());
            Thread stdoutThread = startDrainThread(stdoutCollector, "docker-stdout-" + containerName);
            Thread stderrThread = startDrainThread(stderrCollector, "docker-stderr-" + containerName);

            boolean finished = process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS);
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
        } finally {
            cleanup(tempDir);
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

    private List<String> buildCommand(String containerName, Path tempDir, String image, Language language) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        command.add("-i");
        command.add("--network=none");
        command.add("--memory=" + properties.memory());
        command.add("--cpus=" + properties.cpus());
        command.add("--pids-limit=" + properties.pidsLimit());
        command.add("--name");
        command.add(containerName);
        command.add("-v");
        command.add(tempDir.toAbsolutePath() + ":/app:ro");
        command.add(image);
        command.addAll(language.runCommand());
        return command;
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

    private void cleanup(Path tempDir) {
        if (tempDir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn("failed to delete {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("failed to clean up temp dir {}", tempDir, e);
        }
    }
}