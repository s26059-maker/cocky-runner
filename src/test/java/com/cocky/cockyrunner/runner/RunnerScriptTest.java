package com.cocky.cockyrunner.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

        assertThat(content).contains("T=/timing/.timing").doesNotContain("/work/");
        assertThat(content).contains("\"$@\"");
        assertThat(content).startsWith("#!/bin/sh\n");
    }

    @Test
    void createTimingFile_createsAnEmptyFile_worldWritableWhereSupported() throws IOException {
        Path file = RunnerScript.createTimingFile(workDir);

        assertThat(file).isEqualTo(workDir.resolve(RunnerScript.TIMING_FILE_NAME));
        assertThat(file).isRegularFile();
        assertThat(Files.size(file)).isZero();
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            assertThat(perms).contains(PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.GROUP_WRITE);
        }
    }

    @Test
    void clearTiming_truncatesInPlace_keepingTheSameFile() throws IOException {
        Path timingFile = RunnerScript.createTimingFile(workDir);
        Files.writeString(timingFile, "leftover from a previous run");
        Object idBefore = Files.readAttributes(timingFile, java.nio.file.attribute.BasicFileAttributes.class).fileKey();

        RunnerScript.clearTiming(workDir);

        assertThat(timingFile).isRegularFile();
        assertThat(Files.size(timingFile)).isZero();
        // same inode: it is bind-mounted by file, so it must never be deleted and recreated
        // (fileKey is null where the platform doesn't expose one)
        assertThat(Files.readAttributes(timingFile, java.nio.file.attribute.BasicFileAttributes.class).fileKey())
                .isEqualTo(idBefore);
    }

    @Test
    void clearTiming_isAnErrorWhenTheTimingFileIsMissing() {
        assertThatThrownBy(() -> RunnerScript.clearTiming(workDir)).isInstanceOf(IOException.class);
    }

    /**
     * A truncate failure surfaces as a checked {@link IOException} (not swallowed, not
     * wrapped in an unchecked one). A directory in place of the file cannot be opened for
     * writing on any OS or user (including root), so this reproduces the failure portably.
     */
    @Test
    void clearTiming_truncateFailure_isThrownAsIOException() throws IOException {
        Files.createDirectory(workDir.resolve(RunnerScript.TIMING_FILE_NAME));

        assertThatThrownBy(() -> RunnerScript.clearTiming(workDir)).isInstanceOf(IOException.class);
    }

    @Test
    void clearTiming_doesNotFollowLinks_targetNotTruncated(@TempDir Path outside) throws IOException {
        Path victim = Files.writeString(outside.resolve("precious.txt"), "keep me");
        assumeSymlinks(workDir.resolve(RunnerScript.TIMING_FILE_NAME), victim);

        assertThatThrownBy(() -> RunnerScript.clearTiming(workDir)).isInstanceOf(IOException.class);

        assertThat(victim).hasContent("keep me");
        assertThat(Files.isSymbolicLink(workDir.resolve(RunnerScript.TIMING_FILE_NAME))).isTrue();
        assertThat(Files.exists(workDir.resolve(RunnerScript.TIMING_FILE_NAME), LinkOption.NOFOLLOW_LINKS)).isTrue();
    }

    /** Creates {@code link -> target}, skipping the test where the OS won't allow symlinks. */
    static void assumeSymlinks(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            assumeTrue(false, "cannot create symlinks here: " + e);
        }
    }
}
