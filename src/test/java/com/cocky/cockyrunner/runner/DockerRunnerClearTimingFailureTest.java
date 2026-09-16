package com.cocky.cockyrunner.runner;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageDockerProperties;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link DockerRunner#run}'s handling of a {@link RunnerScript#clearTiming}
 * failure - no real Docker daemon needed, since {@code clearTiming()} is the
 * first thing {@code run()} does inside its try block: a deletion failure there
 * short-circuits before any docker process is ever launched, so this reproduces
 * entirely with the filesystem. This is what actually proves the bug the PR
 * review flagged is fixed - {@link RunnerScriptTest} only proves
 * {@code clearTiming()} itself throws a checked {@link IOException}, not that
 * {@code run()} converts it into an {@link ExecutionStatus#ERROR} response
 * instead of letting it propagate.
 */
class DockerRunnerClearTimingFailureTest {

    @TempDir
    Path workDirRoot;

    @Test
    void run_convertsAClearTimingFailureIntoAnErrorResponse_insteadOfPropagating() throws IOException {
        DockerProperties properties = new DockerProperties(
                Map.of(
                        Language.C, new LanguageDockerProperties("gcc:14", 1000),
                        Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 1000)
                ),
                5, "256m", 1.0, 64, 65536, workDirRoot.toString());
        DockerRunner runner = new DockerRunner(properties);

        Path workDir = Files.createDirectory(workDirRoot.resolve("submission"));
        Path timingFile = workDir.resolve(RunnerScript.TIMING_FILE_NAME);
        Files.writeString(timingFile, "leftover from a previous run");
        assertThat(timingFile.toFile().setReadOnly()).as("could not make the fixture file read-only").isTrue();

        LanguageSpec spec = new LanguageSpec("python:3.11-slim", "main.py", null, List.of("python3", "main.py"), 1.0, 1000);

        try {
            ExecutionResponse response = runner.run(workDir, spec, "", 1000L);

            assertThat(response.status()).isEqualTo(ExecutionStatus.ERROR);
            assertThat(response.userWallMs()).isNull();
            assertThat(response.userCpuMs()).isNull();
        } finally {
            timingFile.toFile().setWritable(true);
        }
    }
}
