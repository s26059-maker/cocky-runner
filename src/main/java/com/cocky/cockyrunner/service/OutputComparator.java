package com.cocky.cockyrunner.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Compares a test case's expected output against the actual program output.
 * Both values are normalized before an exact-match comparison is applied:
 * 1) CRLF ("\r\n") line endings are unified to LF ("\n").
 * 2) Trailing whitespace on each line is stripped; leading whitespace is preserved.
 * 3) Trailing blank lines, including a dangling final newline, are stripped.
 */
public class OutputComparator {

    public boolean match(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    private String normalize(String text) {
        String unified = (text == null ? "" : text).replace("\r\n", "\n");

        List<String> lines = new ArrayList<>(Arrays.asList(unified.split("\n", -1)));
        lines.replaceAll(this::stripTrailingWhitespace);

        int end = lines.size();
        while (end > 0 && lines.get(end - 1).isEmpty()) {
            end--;
        }
        return String.join("\n", lines.subList(0, end));
    }

    private String stripTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }
}
