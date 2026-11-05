package com.cocky.cockyrunner.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixtures below use the wrapper script's real {@code .timing} shape: a
 * {@code START}/{@code END} nanosecond marker pair and two {@code BEFORE}/
 * {@code AFTER} blocks, each holding the {@code times} builtin's two lines
 * (shell itself, then accumulated children) - the children's line is the one
 * that carries the user program's CPU time, since it runs as a child of the
 * wrapper shell. Duration tokens are written in the real {@code times} format:
 * six decimal digits (e.g. {@code 0m0.150000s}), per {@code getconf CLK_TCK}
 * = 100 on the gcc:14 / python:3.11-slim images this targets.
 */
class TimingParserTest {

    private static final String TIMING_FILE_NAME = RunnerScript.TIMING_FILE_NAME;

    @TempDir
    Path workDir;

    @Test
    void noTimingFile_returnsUnmeasuredWithoutTouchingTotalWallMs() {
        ExecutionTiming timing = TimingParser.parse(workDir, 725L);

        assertThat(timing.totalWallMs()).isEqualTo(725L);
        assertThat(timing.userWallMs()).isNull();
        assertThat(timing.userCpuMs()).isNull();
        assertThat(timing.overheadMs()).isNull();
    }

    @Test
    void wellFormedFile_parsesWallAndCpuAndOverhead() throws IOException {
        writeTiming(
                "START 1735900000000000000",
                "BEFORE",
                "0m0.000000s 0m0.000000s",
                "0m0.000000s 0m0.000000s",
                "AFTER",
                "0m0.000000s 0m0.010000s",
                "0m0.140000s 0m0.010000s",
                "END 1735900000200000000",
                "CODE 0"
        );

        ExecutionTiming timing = TimingParser.parse(workDir, 625L);

        assertThat(timing.totalWallMs()).isEqualTo(625L);
        assertThat(timing.userWallMs()).isEqualTo(200L);
        assertThat(timing.userCpuMs()).isEqualTo(150L);
        assertThat(timing.overheadMs()).isEqualTo(425L);
    }

    @Test
    void tenDigitStartAndEnd_treatedAsUnsupportedNanoseconds_cpuStillParses() throws IOException {
        writeTiming(
                "START 1735900000",
                "BEFORE",
                "0m0.000000s 0m0.000000s",
                "0m0.000000s 0m0.000000s",
                "AFTER",
                "0m0.000000s 0m0.010000s",
                "0m0.140000s 0m0.010000s",
                "END 1735900000",
                "CODE 0"
        );

        ExecutionTiming timing = TimingParser.parse(workDir, 625L);

        assertThat(timing.userWallMs()).isNull();
        assertThat(timing.userCpuMs()).isEqualTo(150L);
    }

    @Test
    void endBeforeStart_wallDiscardedButCpuSurvives() throws IOException {
        writeTiming(
                "START 1735900000300000000",
                "BEFORE",
                "0m0.000000s 0m0.000000s",
                "0m0.000000s 0m0.000000s",
                "AFTER",
                "0m0.000000s 0m0.010000s",
                "0m0.140000s 0m0.010000s",
                "END 1735900000100000000",
                "CODE 0"
        );

        ExecutionTiming timing = TimingParser.parse(workDir, 625L);

        assertThat(timing.userWallMs()).isNull();
        assertThat(timing.userCpuMs()).isEqualTo(150L);
    }

    @Test
    void beforeBlockMissingChildLine_cpuDiscardedButWallSurvives() throws IOException {
        writeTiming(
                "START 1735900000000000000",
                "BEFORE",
                "0m0.000000s 0m0.000000s",
                // BEFORE's child (children-accumulated) line is missing here -
                // the next line is the AFTER marker instead of a duration line.
                "AFTER",
                "0m0.000000s 0m0.010000s",
                "0m0.140000s 0m0.010000s",
                "END 1735900000200000000",
                "CODE 0"
        );

        ExecutionTiming timing = TimingParser.parse(workDir, 625L);

        assertThat(timing.userWallMs()).isEqualTo(200L);
        assertThat(timing.userCpuMs()).isNull();
    }

    @Test
    void symlinkedTimingFile_isNotRead_returnsUnmeasured(@TempDir Path outside) throws IOException {
        Path target = outside.resolve("real-timing");
        Files.write(target, List.of(
                "START 1735900000000000000", "BEFORE", "0m0.000000s 0m0.000000s", "0m0.000000s 0m0.000000s",
                "AFTER", "0m0.000000s 0m0.010000s", "0m0.140000s 0m0.010000s",
                "END 1735900000200000000", "CODE 0"), StandardCharsets.UTF_8);
        RunnerScriptTest.assumeSymlinks(workDir.resolve(TIMING_FILE_NAME), target);

        ExecutionTiming timing = TimingParser.parse(workDir, 625L);

        assertThat(timing.totalWallMs()).isEqualTo(625L);
        assertThat(timing.userWallMs()).isNull();
        assertThat(timing.userCpuMs()).isNull();
    }

    @Test
    void emptyFile_returnsUnmeasured() throws IOException {
        Files.createFile(workDir.resolve(TIMING_FILE_NAME));

        ExecutionTiming timing = TimingParser.parse(workDir, 625L);

        assertThat(timing.totalWallMs()).isEqualTo(625L);
        assertThat(timing.userWallMs()).isNull();
        assertThat(timing.userCpuMs()).isNull();
    }

    @Test
    void timingEntryThatIsADirectory_returnsUnmeasured() throws IOException {
        Files.createDirectory(workDir.resolve(TIMING_FILE_NAME));

        ExecutionTiming timing = TimingParser.parse(workDir, 625L);

        assertThat(timing.userWallMs()).isNull();
        assertThat(timing.userCpuMs()).isNull();
    }

    @Test
    void onlyTheFirst4KbAreRead() throws IOException {
        // A valid prefix followed by lots of padding: the markers sit inside the first 4KB.
        StringBuilder sb = new StringBuilder("""
                START 1735900000000000000
                BEFORE
                0m0.000000s 0m0.000000s
                0m0.000000s 0m0.000000s
                AFTER
                0m0.000000s 0m0.010000s
                0m0.140000s 0m0.010000s
                END 1735900000200000000
                CODE 0
                """);
        sb.append("junk\n".repeat(1_000_000));
        Files.writeString(workDir.resolve(TIMING_FILE_NAME), sb);

        ExecutionTiming timing = TimingParser.parse(workDir, 625L);
        assertThat(timing.userWallMs()).isEqualTo(200L);
        assertThat(timing.userCpuMs()).isEqualTo(150L);

        // Markers placed after the 4KB cut are never seen.
        Files.writeString(workDir.resolve(TIMING_FILE_NAME),
                "x\n".repeat(TimingParser.MAX_READ_BYTES) + "START 1735900000000000000\nEND 1735900000200000000\n");
        assertThat(TimingParser.parse(workDir, 625L).userWallMs()).isNull();
    }

    @Test
    void forgedGarbage_returnsNullsWithoutThrowing() throws IOException {
        Files.write(workDir.resolve(TIMING_FILE_NAME), new byte[] {(byte) 0xff, (byte) 0xfe, 0, 1, 2, '\n', 'S'});

        ExecutionTiming timing = TimingParser.parse(workDir, 625L);

        assertThat(timing.totalWallMs()).isEqualTo(625L);
        assertThat(timing.userWallMs()).isNull();
        assertThat(timing.userCpuMs()).isNull();
    }

    private void writeTiming(String... lines) throws IOException {
        Files.write(workDir.resolve(TIMING_FILE_NAME), List.of(lines), StandardCharsets.UTF_8);
    }
}
