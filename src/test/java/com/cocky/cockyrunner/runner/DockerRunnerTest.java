package com.cocky.cockyrunner.runner;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageDockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link DockerRunner} against real Docker (gcc:14 / python:3.11-slim,
 * already pulled in this environment) rather than mocking {@code ProcessBuilder} -
 * this is specifically what proves the wrapper-script wiring (the run command is
 * actually {@code sh /work/run.sh <runCommand>}, the mount is actually writable,
 * {@code .timing} actually gets parsed back) rather than just that DockerRunner
 * calls the pieces in the right order.
 *
 * <p>Tagged {@code "docker"} and excluded from the default {@code test} task
 * (see {@code build.gradle}) since it needs a real Docker daemon - run it via
 * {@code ./gradlew dockerTest}.
 */
@Tag("docker")
class DockerRunnerTest {

    @TempDir
    Path workDirRoot;

    private DockerRunner newRunner() {
        return new DockerRunner(dockerProperties());
    }

    private DockerProperties dockerProperties() {
        return new DockerProperties(
                Map.of(
                        Language.C, new LanguageDockerProperties("gcc:14", 1000),
                        Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 1000)
                ),
                5, "256m", 1.0, 64, 65536, workDirRoot.toString());
    }

    private LanguageSpec spec(Language language) {
        return new LanguageSpecRegistry(dockerProperties()).get(language);
    }

    @Test
    void run_goesThroughTheWrapperAndReturnsAUserTimingBreakdown() {
        // A CPU-bound busy-wait (not time.sleep, which burns wall time but no CPU)
        // so both userWallMs and userCpuMs come back meaningfully non-zero.
        String code = "import time\n" +
                "s = time.time()\n" +
                "while time.time() - s < 0.3:\n" +
                "    pass\n" +
                "print('done')\n";
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(spec(Language.PYTHON), code);
        try {
            ExecutionResponse response = execution.run("", 5000L);

            assertThat(response.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(response.stdout()).isEqualTo("done\n");
            // If the wrapper weren't actually wired in (or its write to /work/.timing
            // were silently failing, e.g. from a stale read-only mount), these two
            // would come back null - unmeasured, same as a plain `python3 main.py`
            // with no wrapper at all.
            assertThat(response.userWallMs()).isNotNull();
            assertThat(response.userCpuMs()).isNotNull();
            assertThat(response.userWallMs()).isGreaterThanOrEqualTo(250L);
            assertThat(response.userCpuMs()).isGreaterThanOrEqualTo(150L);
            // Host-measured total wall time must be at least the in-container wall
            // time it contains.
            assertThat(response.executionTimeMs()).isGreaterThanOrEqualTo(response.userWallMs());
        } finally {
            execution.close();
        }
    }

    @Test
    void consecutiveRuns_secondDoesNotInheritTheFirstsTiming() {
        // One program, one prepared workspace - reused across two run() calls with
        // different stdin, exactly like JudgeService running two test cases against
        // one SubmissionExecution. Busy-waits for the number of seconds given on
        // stdin, so the two runs' own timing is controlled entirely by their input,
        // not by anything left over in the shared workspace from the other.
        String code = "import sys, time\n" +
                "seconds = float(sys.stdin.read().strip() or 0)\n" +
                "s = time.time()\n" +
                "while time.time() - s < seconds:\n" +
                "    pass\n" +
                "print('done')\n";
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(spec(Language.PYTHON), code);
        try {
            ExecutionResponse slow = execution.run("0.4", 5000L);
            assertThat(slow.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(slow.userWallMs()).isNotNull();
            assertThat(slow.userWallMs()).isGreaterThanOrEqualTo(350L);

            ExecutionResponse fast = execution.run("0", 5000L);
            assertThat(fast.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(fast.userWallMs()).isNotNull();
            // If run() failed to clear the previous test case's .timing before this
            // one started (or the wrapper's own truncation somehow didn't happen),
            // this would read back close to `slow`'s ~400ms instead of near-zero.
            assertThat(fast.userWallMs()).isLessThan(slow.userWallMs() / 2);
        } finally {
            execution.close();
        }
    }

    @Test
    void compileFailure_stillWorksWithNoWrapperInvolved() {
        String badCode = "int main() { return 0"; // missing closing brace/semicolon
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(spec(Language.C), badCode);
        try {
            assertThat(execution.compilationFailed()).isTrue();
            assertThat(execution.compileErrorOutput()).isNotBlank();
        } finally {
            execution.close();
        }
    }
}
