package com.cocky.cockyrunner.runner;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The wrapper shell script that a run container is invoked with (wiring that into
 * {@link DockerRunner} is a later step), instead of the user's program directly.
 * It re-measures the user program's own wall/CPU time from inside the container -
 * separating that out from the container startup overhead that the host-side
 * {@code System.currentTimeMillis()} measurement in {@link DockerRunner#run}
 * can't distinguish from it - and writes the result to {@code /work/.timing}
 * rather than stdout/stderr, so the user program's own output stays
 * uncontaminated. {@link TimingParser} reads that file back on the host side
 * once the container exits.
 *
 * <p>The script itself lives at {@code src/main/resources/runner/run.sh} as a
 * real file (not a Java string literal), so it can be read, edited and diffed
 * like any other shell script. {@link #writeTo} copies it byte-for-byte into the
 * submission workspace rather than through a text API, which preserves its LF
 * line endings regardless of what platform the JVM runs on - a text-mode write
 * here would risk translating them to CRLF on Windows, which {@code /bin/sh}
 * inside the (Debian-based) container would then choke on.
 */
public final class RunnerScript {

    /**
     * Name the script is written under in the submission workspace, and the name
     * it will be invoked by, e.g. {@code sh /work/run.sh ...}.
     */
    public static final String FILE_NAME = "run.sh";

    /** Name of the file the script writes its timing breakdown to. */
    public static final String TIMING_FILE_NAME = ".timing";

    private static final String RESOURCE_PATH = "/runner/run.sh";

    private RunnerScript() {
    }

    /**
     * Writes the wrapper script into {@code workDir} as {@value #FILE_NAME}.
     *
     * <p>No execute permission bit is set: the script is invoked as
     * {@code sh /work/run.sh ...} rather than executed directly, so the bit isn't
     * needed, and skipping it avoids a POSIX-only file-permission API that has no
     * real equivalent on Windows, where this workspace is created before it's
     * ever bind-mounted into a container.
     */
    public static void writeTo(Path workDir) {
        try (InputStream in = RunnerScript.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("runner script resource not found on classpath: " + RESOURCE_PATH);
            }
            Files.copy(in, workDir.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + FILE_NAME + " into " + workDir, e);
        }
    }

    /**
     * Deletes the previous run's {@value #TIMING_FILE_NAME} from {@code workDir},
     * if present. The wrapper script itself truncates this file at the start of
     * every run ({@code : > "$T"}), which already keeps a stale reading from one
     * test case leaking into the next within the same submission; this is for
     * callers that want the workspace clean before that first run even happens.
     */
    public static void clearTiming(Path workDir) {
        try {
            Files.deleteIfExists(workDir.resolve(TIMING_FILE_NAME));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to clear " + TIMING_FILE_NAME + " in " + workDir, e);
        }
    }
}
