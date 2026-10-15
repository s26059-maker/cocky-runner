package com.cocky.cockyrunner.util;

/**
 * Removes the container's "/work/" prefix from user-visible compiler/stderr text
 * (e.g. {@code File "/work/main.py"} becomes {@code File "main.py"}), so the sandbox
 * layout doesn't leak into /api/v1 responses. Applied next to {@link TextTruncator}.
 */
public final class WorkPathScrubber {

    private static final String WORK_PREFIX = "/work/";

    private WorkPathScrubber() {
    }

    public static String scrub(String text) {
        return text == null ? null : text.replace(WORK_PREFIX, "");
    }

    /** Scrubs, then caps the length - the standard treatment for user-visible output. */
    public static String scrubAndTruncate(String text) {
        return TextTruncator.truncate(scrub(text));
    }
}
