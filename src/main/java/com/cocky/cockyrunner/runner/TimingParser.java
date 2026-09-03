package com.cocky.cockyrunner.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the {@code .timing} file that {@link RunnerScript}'s wrapper writes
 * inside the container's {@code /work} directory, breaking the host-measured
 * wall time down into the user program's own wall time and CPU time (see
 * {@link ExecutionTiming}).
 *
 * <p>The file is scanned for its {@code START}/{@code END}/{@code BEFORE}/
 * {@code AFTER} markers rather than read by fixed line number, so a malformed
 * or partially-written file degrades rather than misreads. Wall clock and CPU
 * time are determined independently of each other: a problem with one doesn't
 * discard the other.
 *
 * <p>The file simply not existing - most commonly a timeout, where the wrapper
 * never got far enough to write it - is the normal "unmeasured" outcome and is
 * not logged. A file that exists but can't be fully interpreted is logged at
 * warn, with only the affected field(s) set to null.
 */
public final class TimingParser {

    private static final Logger log = LoggerFactory.getLogger(TimingParser.class);

    private static final String START_PREFIX = "START ";
    private static final String END_PREFIX = "END ";
    private static final String BEFORE_MARKER = "BEFORE";
    private static final String AFTER_MARKER = "AFTER";

    /**
     * {@code date +%s%N} should yield 19 digits (seconds since epoch, currently
     * 10 digits, followed by 9 digits of nanoseconds). 18 is accepted as a small
     * safety margin. Anything shorter means the container's {@code date} doesn't
     * support {@code %N} - dropping this check would silently misread a
     * seconds-only value as nanoseconds and report every program's wall time as
     * 0ms.
     */
    private static final int MIN_NANOS_DIGITS = 18;

    /**
     * Matches one {@code times}-style duration token, e.g. {@code 0m0.150000s}.
     * The decimal digit count is deliberately not fixed in the pattern (in
     * practice it's always 6, per {@code getconf CLK_TCK} = 100), so this parser
     * makes no assumption about how many are printed.
     */
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)m(\\d+(?:\\.\\d+)?)s");

    private TimingParser() {
    }

    /**
     * @param totalWallMs the host-measured wall time to carry through into the
     *                    result regardless of what (if anything) the timing file
     *                    yields
     */
    public static ExecutionTiming parse(Path workDir, long totalWallMs) {
        Path timingFile = workDir.resolve(RunnerScript.TIMING_FILE_NAME);
        List<String> lines;
        try {
            if (!Files.exists(timingFile)) {
                return ExecutionTiming.unmeasured(totalWallMs);
            }
            lines = Files.readAllLines(timingFile);
        } catch (IOException e) {
            log.warn("failed to read timing file {}", timingFile, e);
            return ExecutionTiming.unmeasured(totalWallMs);
        }

        Long userWallMs = parseWallMs(lines, timingFile);
        Long userCpuMs = parseCpuMs(lines, timingFile);
        return new ExecutionTiming(totalWallMs, userWallMs, userCpuMs);
    }

    private static Long parseWallMs(List<String> lines, Path timingFile) {
        Long startNs = findMarkerNanos(lines, START_PREFIX, timingFile);
        Long endNs = findMarkerNanos(lines, END_PREFIX, timingFile);
        if (startNs == null || endNs == null) {
            return null;
        }
        if (endNs < startNs) {
            log.warn("timing file {} has END ({}) before START ({})", timingFile, endNs, startNs);
            return null;
        }
        return (endNs - startNs) / 1_000_000L;
    }

    private static Long findMarkerNanos(List<String> lines, String prefix, Path timingFile) {
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                String token = line.substring(prefix.length()).trim();
                if (token.length() < MIN_NANOS_DIGITS || !token.chars().allMatch(Character::isDigit)) {
                    log.warn("timing file {} has an invalid {} marker value: '{}'", timingFile, prefix.trim(), token);
                    return null;
                }
                try {
                    return Long.parseLong(token);
                } catch (NumberFormatException e) {
                    log.warn("timing file {} has an unparseable {} marker value: '{}'", timingFile, prefix.trim(), token);
                    return null;
                }
            }
        }
        log.warn("timing file {} is missing a {} marker", timingFile, prefix.trim());
        return null;
    }

    private static Long parseCpuMs(List<String> lines, Path timingFile) {
        Double before = childCpuSeconds(lines, BEFORE_MARKER, timingFile);
        Double after = childCpuSeconds(lines, AFTER_MARKER, timingFile);
        if (before == null || after == null) {
            return null;
        }
        if (after < before) {
            log.warn("timing file {} has {} child CPU time before {} child CPU time", timingFile, AFTER_MARKER, BEFORE_MARKER);
            return null;
        }
        return Math.round((after - before) * 1000.0);
    }

    /**
     * Finds a marker line (e.g. {@code BEFORE}) and reads the child-process line
     * two lines below it: the {@code times} builtin prints two lines (the shell
     * itself, then its accumulated children), so the child line - the one that
     * will reflect the user program's CPU time once it has run as a child of this
     * shell - is the second of the two. Scans for the marker rather than
     * assuming a fixed line number, and returns null (with a warn log) if the
     * marker is missing or the file doesn't have a usable line at that position.
     *
     * <p>This depends on a specific {@code times} output shape, worth spelling
     * out explicitly since nothing enforces it at compile time:
     * <ul>
     *   <li>Exactly two lines, in order: the shell's own user/sys time, then its
     *       children's <em>accumulated</em> user/sys time. This is what the run
     *       wrapper's own two {@code times} calls (see {@link RunnerScript})
     *       produce, and it's the second line of each that turns into the user
     *       program's CPU time once it's run as this shell's child.</li>
     *   <li>Verified against dash inside the {@code gcc:14} and
     *       {@code python:3.11-slim} images specifically - both Debian, both
     *       {@code /bin/sh} -> dash. A different shell (or a non-dash
     *       {@code /bin/sh}) is not guaranteed to print this same two-line shape.</li>
     *   <li>If the shape doesn't match - a different shell, a future image
     *       change - this returns null rather than misreading it, and only the
     *       CPU figure is lost; wall-clock timing (parsed independently, from the
     *       START/END markers) is unaffected. This is the intended degradation,
     *       not a bug: see the independence note in the class-level doc.</li>
     *   <li>CPU time is display-only. Nothing in the judging path
     *       ({@link com.cocky.cockyrunner.service.JudgeService#judgeCase}) reads
     *       {@code userCpuMs} - AC/WA/TLE/RE are decided by stdout comparison and
     *       the host-enforced wall-clock timeout, so a null (or wrong) CPU
     *       reading here can never flip a verdict.</li>
     * </ul>
     */
    private static Double childCpuSeconds(List<String> lines, String marker, Path timingFile) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(marker)) {
                int childLineIndex = i + 2;
                if (childLineIndex >= lines.size()) {
                    log.warn("timing file {} is truncated: no child-process times line after {}", timingFile, marker);
                    return null;
                }
                String childLine = lines.get(childLineIndex);
                Double seconds = sumDurations(childLine);
                if (seconds == null) {
                    log.warn("timing file {} has an unparseable times line after {}: '{}'", timingFile, marker, childLine);
                }
                return seconds;
            }
        }
        log.warn("timing file {} is missing a {} marker", timingFile, marker);
        return null;
    }

    /** Sums every {@code MmS.ssssss s} token on the line (user + sys), or null if none matched. */
    private static Double sumDurations(String line) {
        Matcher matcher = DURATION_PATTERN.matcher(line);
        double total = 0.0;
        int count = 0;
        while (matcher.find()) {
            long minutes = Long.parseLong(matcher.group(1));
            double seconds = Double.parseDouble(matcher.group(2));
            total += minutes * 60.0 + seconds;
            count++;
        }
        return count == 0 ? null : total;
    }
}
