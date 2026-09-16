package com.cocky.cockyrunner.runner;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageDockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link DockerRunner} against real Docker with the JAVA
 * {@link LanguageSpec} (eclipse-temurin:21-jdk, already pulled in this
 * environment) - the JAVA counterpart to {@link DockerRunnerTest}, covering the
 * compile-once-run-N path plus the AC/CE/RE/TLE shapes specific to javac/java
 * (public-class-name mismatch, deep recursion needing {@code -Xss}, multiple
 * generated {@code .class} files).
 *
 * <p>Tagged {@code "docker"} and excluded from the default {@code test} task -
 * run via {@code ./gradlew dockerTest}.
 */
@Tag("docker")
class DockerRunnerJavaTest {

    private static final Logger log = LoggerFactory.getLogger(DockerRunnerJavaTest.class);

    @TempDir
    Path workDirRoot;

    private DockerRunner newRunner() {
        return new DockerRunner(dockerProperties());
    }

    private DockerProperties dockerProperties() {
        return new DockerProperties(
                Map.of(
                        Language.C, new LanguageDockerProperties("gcc:14", 1000),
                        Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 1000),
                        Language.JAVA, new LanguageDockerProperties("eclipse-temurin:21-jdk", 2000)
                ),
                5, "256m", 1.0, 64, 65536, workDirRoot.toString());
    }

    private LanguageSpec javaSpec() {
        return new LanguageSpecRegistry(dockerProperties()).get(Language.JAVA);
    }

    private void logTiming(String scenario, ExecutionResponse response) {
        log.info("[java dockerTest] {}: totalWallMs={} userWallMs={} userCpuMs={}",
                scenario, response.executionTimeMs(), response.userWallMs(), response.userCpuMs());
    }

    @Test
    void aPlusB_correctSolution_isAc() {
        String code = "import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        int a = sc.nextInt();\n" +
                "        int b = sc.nextInt();\n" +
                "        System.out.println(a + b);\n" +
                "    }\n" +
                "}\n";
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(javaSpec(), code);
        try {
            assertThat(execution.compilationFailed()).isFalse();
            ExecutionResponse response = execution.run("3 4", 5000L);
            logTiming("aPlusB_correctSolution_isAc", response);

            assertThat(response.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(response.stdout()).isEqualTo("7\n");
        } finally {
            execution.close();
        }
    }

    @Test
    void compileError_isCeWithCompilerOutput() {
        String badCode = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"missing semicolon\")\n" +
                "    }\n" +
                "}\n";
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(javaSpec(), badCode);
        try {
            assertThat(execution.compilationFailed()).isTrue();
            assertThat(execution.compileErrorOutput()).isNotBlank();
            assertThat(execution.compileErrorOutput()).contains("error");
        } finally {
            execution.close();
        }
    }

    @Test
    void publicClassNameMismatch_isCe() {
        // Source file is fixed to Main.java, but this declares "Solution" as
        // the public class - javac itself rejects this (no filename-guessing
        // support is implemented), which is exactly the decided behavior.
        String code = "public class Solution {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"hi\");\n" +
                "    }\n" +
                "}\n";
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(javaSpec(), code);
        try {
            assertThat(execution.compilationFailed()).isTrue();
            assertThat(execution.compileErrorOutput()).contains("Solution");
        } finally {
            execution.close();
        }
    }

    @Test
    void arrayIndexOutOfBounds_isRe() {
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int[] arr = new int[3];\n" +
                "        System.out.println(arr[5]);\n" +
                "    }\n" +
                "}\n";
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(javaSpec(), code);
        try {
            assertThat(execution.compilationFailed()).isFalse();
            ExecutionResponse response = execution.run("", 5000L);
            logTiming("arrayIndexOutOfBounds_isRe", response);

            assertThat(response.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
            assertThat(response.stderr()).contains("ArrayIndexOutOfBoundsException");
        } finally {
            execution.close();
        }
    }

    @Test
    void infiniteLoop_isTle() {
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        while (true) {\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(javaSpec(), code);
        try {
            assertThat(execution.compilationFailed()).isFalse();
            ExecutionResponse response = execution.run("", 2000L);
            logTiming("infiniteLoop_isTle", response);

            assertThat(response.status()).isEqualTo(ExecutionStatus.TIMEOUT);
        } finally {
            execution.close();
        }
    }

    @Test
    void deepRecursion_20000_isAc() {
        // Verifies -Xss8m is actually load-bearing, not just present: repeated
        // measurement (5 runs each, see task investigation) found the default
        // JVM main-thread stack overflows starting at depth 16000 (15000 still
        // succeeds), while -Xss8m succeeds up to depth 100000. Depth 20000 sits
        // comfortably past the default-stack failure point and comfortably
        // under the -Xss8m ceiling, so this only passes AC because the run
        // command carries the explicit -Xss8m flag - unlike depth 10000, which
        // the default stack alone also survives and so proves nothing.
        String code = "public class Main {\n" +
                "    static long rec(int n) {\n" +
                "        if (n == 0) return 0;\n" +
                "        return 1 + rec(n - 1);\n" +
                "    }\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(rec(20000));\n" +
                "    }\n" +
                "}\n";
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(javaSpec(), code);
        try {
            assertThat(execution.compilationFailed()).isFalse();
            ExecutionResponse response = execution.run("", 5000L);
            logTiming("deepRecursion_20000_isAc", response);

            assertThat(response.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(response.stdout()).isEqualTo("20000\n");
        } finally {
            execution.close();
        }
    }

    @Test
    void koreanOutput_isAc() {
        // Regression guard: the image's default locale (en_US.UTF-8) and JDK21's
        // default stdout.encoding (UTF-8, JEP 400) must keep non-ASCII stdout
        // intact with no extra flag - verified byte-for-byte against the same
        // string encoded on the host (see task investigation).
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"한글 테스트: 안녕하세요, 프로그래밍!\");\n" +
                "    }\n" +
                "}\n";
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(javaSpec(), code);
        try {
            assertThat(execution.compilationFailed()).isFalse();
            ExecutionResponse response = execution.run("", 5000L);
            logTiming("koreanOutput_isAc", response);

            assertThat(response.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(response.stdout()).isEqualTo("한글 테스트: 안녕하세요, 프로그래밍!\n");
        } finally {
            execution.close();
        }
    }

    @Test
    void multipleClasses_compileToSeparateClassFilesAndRunAc() throws IOException {
        String code = "class Helper {\n" +
                "    static int square(int x) { return x * x; }\n" +
                "}\n" +
                "public class Main {\n" +
                "    static class Inner {\n" +
                "        static int cube(int x) { return x * x * x; }\n" +
                "    }\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(Helper.square(3) + Inner.cube(2));\n" +
                "    }\n" +
                "}\n";
        DockerRunner runner = newRunner();
        SubmissionExecution execution = runner.prepareSubmission(javaSpec(), code);
        try {
            assertThat(execution.compilationFailed()).isFalse();

            // The submission workspace is the sole subdirectory created under
            // workDirRoot by prepareSubmission() - inspected before close(),
            // which deletes it.
            Path submissionDir;
            try (Stream<Path> children = Files.list(workDirRoot)) {
                submissionDir = children.findFirst()
                        .orElseThrow(() -> new AssertionError("no submission workspace directory found"));
            }
            long classFileCount;
            try (Stream<Path> files = Files.list(submissionDir)) {
                classFileCount = files.filter(p -> p.getFileName().toString().endsWith(".class")).count();
            }
            assertThat(classFileCount).isEqualTo(3); // Main.class, Main$Inner.class, Helper.class

            ExecutionResponse response = execution.run("", 5000L);
            logTiming("multipleClasses_compileToSeparateClassFilesAndRunAc", response);

            assertThat(response.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(response.stdout()).isEqualTo("17\n");
        } finally {
            execution.close();
        }
    }
}
