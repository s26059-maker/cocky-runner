package com.cocky.cockyrunner.diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * javac: {@code Main.java:L: error|warning: msg} (no column). If the output has
 * no such line it is treated as a runtime failure: the first stack frame inside
 * Main.java becomes the ERROR, with the exception's first line as the message
 * (minus the {@code Exception in thread "main"} prefix).
 */
final class JavaDiagnosticParser implements DiagnosticParser {

    private static final Pattern COMPILE = Pattern.compile(
            "^(?:.*[/\\\\])?Main\\.java:(\\d+): (error|warning): (.*)$");
    private static final Pattern FRAME = Pattern.compile("^\\s+at .*\\(Main\\.java:(\\d+)\\)\\s*$");
    private static final Pattern THREAD_PREFIX = Pattern.compile("^Exception in thread \"[^\"]*\" ");

    @Override
    public List<Diagnostic> parse(String output, String source) {
        List<String> lines = DiagnosticText.lines(output);

        List<Diagnostic> result = new ArrayList<>();
        for (String line : lines) {
            Matcher m = COMPILE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            Integer lineNo = DiagnosticText.toInt(m.group(1));
            if (lineNo != null && lineNo >= 1) {
                Severity severity = m.group(2).equals("warning") ? Severity.WARNING : Severity.ERROR;
                result.add(new Diagnostic(lineNo, null, severity, m.group(3).strip()));
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        return runtime(lines);
    }

    private static List<Diagnostic> runtime(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = FRAME.matcher(lines.get(i));
            if (!m.matches()) {
                continue;
            }
            Integer lineNo = DiagnosticText.toInt(m.group(1));
            String message = exceptionHeader(lines, i);
            if (lineNo == null || lineNo < 1 || message == null) {
                return List.of();
            }
            return List.of(new Diagnostic(lineNo, null, Severity.ERROR, message));
        }
        return List.of();
    }

    /** The nearest non-indented line above the first frame: the exception's first line. */
    private static String exceptionHeader(List<String> lines, int frameIndex) {
        for (int i = frameIndex - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.isBlank() || Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            return THREAD_PREFIX.matcher(line).replaceFirst("").strip();
        }
        return null;
    }
}
