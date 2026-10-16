package com.cocky.cockyrunner.diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * gcc: {@code main.c:L:C: error|warning|note: msg}. Lines that don't point at
 * main.c ("In function 'main':", code excerpts, headers) are ignored.
 */
final class CDiagnosticParser implements DiagnosticParser {

    private static final Pattern LINE = Pattern.compile(
            "^(?:.*[/\\\\])?main\\.c:(\\d+):(?:(\\d+):)? (fatal error|error|warning|note): (.*)$");

    @Override
    public List<Diagnostic> parse(String output, String source) {
        List<Diagnostic> result = new ArrayList<>();
        for (String line : DiagnosticText.lines(output)) {
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            Integer lineNo = DiagnosticText.toInt(m.group(1));
            if (lineNo == null || lineNo < 1) {
                continue;
            }
            Integer column = m.group(2) == null ? null : DiagnosticText.toInt(m.group(2));
            if (column != null && column < 1) {
                column = null;
            }
            result.add(new Diagnostic(lineNo, column, severity(m.group(3)), m.group(4).strip()));
        }
        return result;
    }

    private static Severity severity(String kind) {
        return switch (kind) {
            case "warning" -> Severity.WARNING;
            case "note" -> Severity.INFO;
            default -> Severity.ERROR;
        };
    }
}
