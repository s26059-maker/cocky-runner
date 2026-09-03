package com.cocky.cockyrunner.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RunnerScriptTest {

    @TempDir
    Path workDir;

    @Test
    void writeTo_writesRunShWithNoCarriageReturns() throws IOException {
        RunnerScript.writeTo(workDir);

        Path script = workDir.resolve(RunnerScript.FILE_NAME);
        assertThat(script).exists();

        byte[] bytes = Files.readAllBytes(script);
        for (byte b : bytes) {
            assertThat(b).as("script must use LF line endings only, no \\r bytes").isNotEqualTo((byte) '\r');
        }
    }

    @Test
    void writeTo_producesAScriptThatInvokesTheUserProgramAndWritesTiming() throws IOException {
        RunnerScript.writeTo(workDir);

        String content = Files.readString(workDir.resolve(RunnerScript.FILE_NAME));

        assertThat(content).contains("/work/.timing");
        assertThat(content).contains("\"$@\"");
        assertThat(content).startsWith("#!/bin/sh\n");
    }

    @Test
    void clearTiming_deletesExistingTimingFile() throws IOException {
        Path timingFile = workDir.resolve(RunnerScript.TIMING_FILE_NAME);
        Files.writeString(timingFile, "leftover from a previous run");

        RunnerScript.clearTiming(workDir);

        assertThat(timingFile).doesNotExist();
    }

    @Test
    void clearTiming_isANoOpWhenNoTimingFileExists() {
        assertThatCode(() -> RunnerScript.clearTiming(workDir)).doesNotThrowAnyException();
    }
}
