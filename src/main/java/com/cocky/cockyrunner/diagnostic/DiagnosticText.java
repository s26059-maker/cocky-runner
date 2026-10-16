package com.cocky.cockyrunner.diagnostic;

import java.util.List;

final class DiagnosticText {

    private DiagnosticText() {
    }

    static List<String> lines(String output) {
        if (output == null || output.isEmpty()) {
            return List.of();
        }
        return List.of(output.split("\\R", -1));
    }

    /** Null instead of an exception for numbers that are absent or overflow an int. */
    static Integer toInt(String digits) {
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
