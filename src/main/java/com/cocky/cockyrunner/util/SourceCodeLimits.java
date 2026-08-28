package com.cocky.cockyrunner.util;

import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;

/**
 * Bounds on submitted source code, enforced at the controller boundary - before
 * anything is written to a workspace or handed to Docker - so an oversized
 * submission is rejected as a normal 400 rather than failing deeper in the
 * pipeline (or worse, succeeding but wasting a compile/run cycle on something
 * that was never going to be accepted).
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
