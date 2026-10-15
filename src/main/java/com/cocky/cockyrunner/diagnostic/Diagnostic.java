package com.cocky.cockyrunner.diagnostic;

/**
 * A compiler/runtime message pinned to a source line, for the editor to mark.
 *
 * @param line   1-based line in the submitted source
 * @param column 1-based column, or null when the tool only reports a line
 */
public record Diagnostic(
        int line,
        Integer column,
        Severity severity,
        String message
) {
}
