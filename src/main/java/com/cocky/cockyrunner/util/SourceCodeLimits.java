package com.cocky.cockyrunner.util;

import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;

/**
 * Bounds on submitted source code, enforced at the controller boundary - before
 * anything is written to a workspace or handed to Docker - so an oversized
 * submission is rejected as a normal 400 rather than failing deeper in the
 * pipeline (or worse, succeeding but wasting a compile/run cycle on something
 * that was never going to be accepted).
 *
 * <p>The limit is a character count ({@code String.length()}, i.e. UTF-16 code
 * units) - not a UTF-8 byte count, and not a stored/serialized size in bytes.
 * A single character can be 1-4 UTF-8 bytes, so the same 65536-character
 * submission can occupy a different number of bytes depending on its content;
 * this class doesn't bound that. {@link com.cocky.cockyrunner.util.TextTruncator}
 * uses the same code-unit convention for the same reason, so the two policies
 * stay consistent with each other.
 */
public final class SourceCodeLimits {

    /** Cap in characters (String.length(), i.e. UTF-16 code units), not UTF-8 bytes. */
    public static final int MAX_SOURCE_LENGTH = 65536;

    private SourceCodeLimits() {
    }

    /**
     * Throws {@link InvalidExecutionRequestException} if {@code code} exceeds
     * {@link #MAX_SOURCE_LENGTH}. A null or blank {@code code} is left for the
     * caller's own blank-code check to reject - this only enforces the upper bound.
     */
    public static void validateLength(String code) {
        if (code != null && code.length() > MAX_SOURCE_LENGTH) {
            throw new InvalidExecutionRequestException(
                    "code must not exceed " + MAX_SOURCE_LENGTH + " characters (was " + code.length() + ")");
        }
    }
}
