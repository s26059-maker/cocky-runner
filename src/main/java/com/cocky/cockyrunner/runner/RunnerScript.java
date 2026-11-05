package com.cocky.cockyrunner.runner;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * The wrapper shell script that a run container is invoked with (wiring that into
 * {@link DockerRunner} is a later step), instead of the user's program directly.
 * It re-measures the user program's own wall/CPU time from inside the container -
 * separating that out from the container startup overhead that the host-side
 * {@code System.currentTimeMillis()} measurement in {@link DockerRunner#run}
 * can't distinguish from it - and writes the result to {@code /timing/.timing}
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

    /**
     * Name of the file the script writes its timing breakdown to. Only this single file
     * is bind-mounted (read-write) into a run container, at {@code /timing/.timing};
     * the container's root filesystem and the workspace are read-only, so it can't be
     * unlinked, renamed or replaced, and nothing else can be created next to it.
     */
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
     * Creates the empty timing file inside {@code timingDir} (the host directory that
     * belongs to one submission; only this one file of it is ever mounted into a
     * container, at {@code /timing/.timing}). The file is made world-writable (666)
     * because the container runs as an unrelated uid; the directory itself is not.
     * The file must exist before the first run: Docker would otherwise create a
     * directory at a missing bind-mount source.
     */
    public static Path createTimingFile(Path timingDir) throws IOException {
        Path file = timingDir.resolve(TIMING_FILE_NAME);
        Files.createFile(file);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-rw-"));
        } catch (UnsupportedOperationException e) {
            // non-POSIX filesystem (Windows host): nothing to do
        }
        return file;
    }

    /**
     * Truncates the timing file to zero length, in place, so nothing the previous test
     * case's program wrote there can reach this one (the wrapper script itself also
     * truncates it at the start of every run, {@code : > "$T"}; this is for the case
     * where the container never starts). It must stay the same inode: it is bind-mounted
     * into containers by file, so it is never deleted/recreated.
     *
     * <p>Opened with NOFOLLOW_LINKS, so a link in its place is an error rather than a
     * truncation of the link's target. A missing file or one that cannot be truncated
     * (e.g. a directory) fails.
     *
     * <p>Thrown as a checked {@link IOException} rather than wrapped in an
     * unchecked one - unlike {@link #writeTo}, this runs on every single test
     * case, inside {@link DockerRunner#run}'s existing try/catch, so a caller
     * there is expected to handle the failure (translate it into an ERROR
     * response) rather than let it propagate as an uncaught exception.
     */
    public static void clearTiming(Path timingDir) throws IOException {
        Path file = timingDir.resolve(TIMING_FILE_NAME);
        try (SeekableByteChannel ignored = Files.newByteChannel(file, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
            // opening with TRUNCATE_EXISTING is the whole operation
        }
    }
}
