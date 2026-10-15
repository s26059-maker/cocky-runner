package com.cocky.cockyrunner.diagnostic;

import com.cocky.cockyrunner.domain.Language;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entry point: parses a language's compiler/runtime output into {@link Diagnostic}s.
 * Never throws; anything unparseable (or without a line number) gives an empty list.
 * Run it on the raw output, before TextTruncator / path scrubbing.
 */
public final class Diagnostics {

    public static final int MAX_DIAGNOSTICS = 50;
    static final int MAX_MESSAGE_LENGTH = 500;

    private static final DiagnosticParser C = new CDiagnosticParser();
    private static final DiagnosticParser JAVA = new JavaDiagnosticParser();
    private static final DiagnosticParser PYTHON = new PythonDiagnosticParser();

    private Diagnostics() {
    }

    public static List<Diagnostic> parse(Language language, String output) {
        return parse(language, output, null);
    }

    /** {@code source} is optional; it only lets Python SyntaxError columns be resolved. */
    public static List<Diagnostic> parse(Language language, String output, String source) {
        if (language == null || output == null || output.isBlank()) {
            return List.of();
        }
        try {
            DiagnosticParser parser = switch (language) {
                case C -> C;
                case JAVA -> JAVA;
                case PYTHON -> PYTHON;
            };
            return normalize(parser.parse(output, source));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** Drops same-line/same-message repeats, caps message length and the total count. */
    private static List<Diagnostic> normalize(List<Diagnostic> parsed) {
        Set<String> seen = new HashSet<>();
        List<Diagnostic> result = new ArrayList<>();
        for (Diagnostic d : parsed) {
            String message = d.message().length() > MAX_MESSAGE_LENGTH
                    ? d.message().substring(0, MAX_MESSAGE_LENGTH) + "..."
                    : d.message();
            if (!seen.add(d.line() + ":" + message)) {
                continue;
            }
            result.add(new Diagnostic(d.line(), d.column(), d.severity(), message));
            if (result.size() >= MAX_DIAGNOSTICS) {
                break;
            }
        }
        return List.copyOf(result);
    }
}
