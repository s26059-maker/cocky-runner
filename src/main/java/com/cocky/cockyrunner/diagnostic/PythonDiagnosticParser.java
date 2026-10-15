package com.cocky.cockyrunner.diagnostic;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python tracebacks. The location is the LAST frame pointing at main.py (the
 * deepest user-code frame); the message is the exception line that follows the
 * final frame. For SyntaxError-style output (no "Traceback" header) the caret
 * line gives the column.
 */
final class PythonDiagnosticParser implements DiagnosticParser {

    private static final Pattern FRAME = Pattern.compile("^\\s*File \"([^\"]*)\", line (\\d+)(?:,.*)?$");
    private static final Pattern MAIN_PY = Pattern.compile("(?:.*[/\\\\])?main\\.py");
    private static final Pattern CARETS = Pattern.compile("\\s*\\^+\\s*");
    /** Python prints the offending source line indented by exactly 4 spaces. */
    private static final int SOURCE_INDENT = 4;

    @Override
    public List<Diagnostic> parse(String output, String source) {
        List<String> lines = DiagnosticText.lines(output);

        int lastFrame = -1;
        int lastUserFrame = -1;
        Matcher userFrame = null;
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = FRAME.matcher(lines.get(i));
            if (!m.matches()) {
                continue;
            }
            lastFrame = i;
            if (MAIN_PY.matcher(m.group(1)).matches()) {
                lastUserFrame = i;
                userFrame = m;
            }
        }
        if (userFrame == null) {
            return List.of();
        }
        Integer lineNo = DiagnosticText.toInt(userFrame.group(2));
        String message = exceptionLine(lines, lastFrame);
        if (lineNo == null || lineNo < 1 || message == null) {
            return List.of();
        }

        Integer column = null;
        boolean syntaxStyle = lines.stream().noneMatch(l -> l.startsWith("Traceback"));
        if (syntaxStyle && lastUserFrame == lastFrame) {
            column = syntaxColumn(lines, lastUserFrame, lineNo, source);
        }
        return List.of(new Diagnostic(lineNo, column, Severity.ERROR, message));
    }

    /** First non-indented line after the final frame (frames, source and carets are all indented). */
    private static String exceptionLine(List<String> lines, int lastFrame) {
        for (int i = lastFrame + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                return line.strip();
            }
        }
        return null;
    }

    /**
     * The frame line is followed by the echoed source line and a caret line. The
     * echo has its leading whitespace stripped, so the column is only trusted when
     * the submitted source confirms that echo; otherwise it is ambiguous and null.
     */
    private static Integer syntaxColumn(List<String> lines, int frameIndex, int lineNo, String source) {
        if (source == null || frameIndex + 2 >= lines.size()) {
            return null;
        }
        String echo = lines.get(frameIndex + 1);
        String caretLine = lines.get(frameIndex + 2);
        int caret = caretLine.indexOf('^');
        if (caret < SOURCE_INDENT || !CARETS.matcher(caretLine).matches() || !echo.startsWith("    ")) {
            return null;
        }
        String[] sourceLines = source.split("\\R", -1);
        if (lineNo > sourceLines.length) {
            return null;
        }
        String original = sourceLines[lineNo - 1];
        String stripped = original.stripLeading();
        if (!echo.substring(SOURCE_INDENT).stripTrailing().equals(stripped.stripTrailing())) {
            return null;
        }
        int indent = original.length() - stripped.length();
        return caret - SOURCE_INDENT + indent + 1;
    }
}
