package com.cocky.cockyrunner.util;

/**
 * Caps user-visible text (execution stderr, compiler stderr) to a fixed length
 * before it leaves the judge. Shared so every place that exposes such text -
 * currently {@link com.cocky.cockyrunner.service.JudgeService}, for both a
 * failing sample case's stderr and a compile error's stderr - applies the same
 * limit the same way.
 */
public final class TextTruncator {

    /**
     * Cap in characters (String.length(), i.e. UTF-16 code units), not UTF-8 bytes -
     * truncation is character-based so it never cuts a multi-byte character (e.g.
     * Korean) in half.
     */
    public static final int MAX_LENGTH = 4000;

    private static final String TRUNCATION_SUFFIX = "\n... (truncated)";

    private TextTruncator() {
    }

    /** Returns {@code text} unchanged if it's null or within {@link #MAX_LENGTH}. */
    public static String truncate(String text) {
        if (text == null || text.length() <= MAX_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_LENGTH) + TRUNCATION_SUFFIX;
    }
}
