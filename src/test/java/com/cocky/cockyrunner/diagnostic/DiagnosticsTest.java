package com.cocky.cockyrunner.diagnostic;

import com.cocky.cockyrunner.domain.Language;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The src/test/resources/diagnostics/*.txt fixtures are REAL output captured from
 * gcc:14, eclipse-temurin:21-jdk and python:3.11-slim running the same commands the
 * runner uses (main.c / Main.java / main.py inside /work).
 */
class DiagnosticsTest {

    private static String fixture(String name) {
        try (InputStream in = DiagnosticsTest.class.getResourceAsStream("/diagnostics/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- C ----

    @Test
    void c_compileErrors_haveLineColumnAndNoteIsInfo() {
        List<Diagnostic> result = Diagnostics.parse(Language.C, fixture("c_error"));

        assertThat(result).containsExactly(
                new Diagnostic(5, 16, Severity.ERROR, "'y' undeclared (first use in this function)"),
                new Diagnostic(5, 16, Severity.INFO,
                        "each undeclared identifier is reported only once for each function it appears in"),
                new Diagnostic(9, 19, Severity.ERROR, "expected ';' before 'return'"));
    }

    @Test
    void c_warnings_areWarningSeverity() {
        List<Diagnostic> result = Diagnostics.parse(Language.C, fixture("c_warning"));

        assertThat(result).containsExactly(
                new Diagnostic(2, 2, Severity.WARNING, "#warning custom note [-Wcpp]"),
                new Diagnostic(5, 15, Severity.WARNING,
                        "initialization discards 'const' qualifier from pointer target type [-Wdiscarded-qualifiers]"));
    }

    @Test
    void c_inFunctionLinesAndOtherFilesAreIgnored() {
        String output = "main.c: In function 'main':\n"
                + "/usr/include/stdio.h:10:5: error: not ours\n"
                + "/usr/bin/ld: main.c:(.text+0x10): undefined reference to `foo'\n"
                + "collect2: error: ld returned 1 exit status\n";

        assertThat(Diagnostics.parse(Language.C, output)).isEmpty();
    }

    @Test
    void c_lineWithoutColumn_hasNullColumn() {
        assertThat(Diagnostics.parse(Language.C, "main.c:7: error: something odd"))
                .containsExactly(new Diagnostic(7, null, Severity.ERROR, "something odd"));
    }

    // ---- Java ----

    @Test
    void java_compileErrors_haveLineOnly() {
        List<Diagnostic> result = Diagnostics.parse(Language.JAVA, fixture("j_compile"));

        assertThat(result).containsExactly(
                new Diagnostic(3, null, Severity.ERROR, "incompatible types: String cannot be converted to int"),
                new Diagnostic(4, null, Severity.ERROR, "cannot find symbol"));
    }

    @Test
    void java_runtimeException_usesFirstMainFrameAndExceptionFirstLine() {
        List<Diagnostic> result = Diagnostics.parse(Language.JAVA, fixture("j_runtime"));

        assertThat(result).containsExactly(new Diagnostic(2, null, Severity.ERROR,
                "java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 2"));
    }

    @Test
    void java_runtimeFramesInsideTheJdkAreSkippedUntilMain() {
        String output = "Exception in thread \"main\" java.lang.NumberFormatException: For input string: \"x\"\n"
                + "\tat java.base/java.lang.NumberFormatException.forInputString(NumberFormatException.java:67)\n"
                + "\tat java.base/java.lang.Integer.parseInt(Integer.java:661)\n"
                + "\tat Main.main(Main.java:6)\n";

        assertThat(Diagnostics.parse(Language.JAVA, output)).containsExactly(new Diagnostic(6, null,
                Severity.ERROR, "java.lang.NumberFormatException: For input string: \"x\""));
    }

    @Test
    void java_runtimeWithoutMainFrame_isEmpty() {
        String output = "Exception in thread \"main\" java.lang.StackOverflowError\n"
                + "\tat java.base/java.lang.Foo.bar(Foo.java:1)\n";

        assertThat(Diagnostics.parse(Language.JAVA, output)).isEmpty();
    }

    // ---- Python ----

    @Test
    void python_runtimeError_usesLastMainPyFrameAndExceptionLine() {
        List<Diagnostic> result = Diagnostics.parse(Language.PYTHON, fixture("py_runtime"));

        assertThat(result).containsExactly(
                new Diagnostic(2, null, Severity.ERROR, "ZeroDivisionError: division by zero"));
    }

    @Test
    void python_customExceptionWithMultilineMessage_usesFirstExceptionLine() {
        assertThat(Diagnostics.parse(Language.PYTHON, fixture("py_custom")))
                .containsExactly(new Diagnostic(2, null, Severity.ERROR, "MyErr: bad"));
    }

    @Test
    void python_syntaxError_columnFromCaret() {
        String source = "def f(x):\n    return x +\n\nprint(f(1))\n";

        assertThat(Diagnostics.parse(Language.PYTHON, fixture("py_syntax"), source))
                // stripped col 11 ("return x +" is 10 chars) + the source line's 4-space indent
                .containsExactly(new Diagnostic(2, 15, Severity.ERROR, "SyntaxError: invalid syntax"));
    }

    @Test
    void python_syntaxError_withoutSourceHasNoColumn() {
        assertThat(Diagnostics.parse(Language.PYTHON, fixture("py_syntax")))
                .containsExactly(new Diagnostic(2, null, Severity.ERROR, "SyntaxError: invalid syntax"));
    }

    @Test
    void python_syntaxError_columnAccountsForStrippedIndentation() {
        // Python echoes the line without its leading whitespace; the source is needed to put it back.
        String source = "def f():\n    x = [1,2\nprint(x)\n";
        String output = "  File \"/work/main.py\", line 2\n    x = [1,2\n        ^\nSyntaxError: '[' was never closed\n";

        assertThat(Diagnostics.parse(Language.PYTHON, output, source))
                .containsExactly(new Diagnostic(2, 9, Severity.ERROR, "SyntaxError: '[' was never closed"));
    }

    @Test
    void python_syntaxError_unclosedBracket() {
        String source = "x = [1,2\nprint(x)\n";

        assertThat(Diagnostics.parse(Language.PYTHON, fixture("py_unclosed"), source))
                .containsExactly(new Diagnostic(1, 5, Severity.ERROR, "SyntaxError: '[' was never closed"));
    }

    @Test
    void python_indentationError() {
        String source = "def f():\nprint(\"a\")\n";

        assertThat(Diagnostics.parse(Language.PYTHON, fixture("py_indent"), source)).containsExactly(
                new Diagnostic(2, 1, Severity.ERROR,
                        "IndentationError: expected an indented block after function definition on line 1"));
    }

    @Test
    void python_columnIsNullWhenEchoDoesNotMatchSource() {
        String source = "something else entirely\nand more\n";

        assertThat(Diagnostics.parse(Language.PYTHON, fixture("py_syntax"), source))
                .extracting(Diagnostic::column).containsExactly((Integer) null);
    }

    @Test
    void python_pathWithoutWorkPrefixAlsoMatches() {
        String output = "Traceback (most recent call last):\n  File \"main.py\", line 3, in <module>\nKeyError: 'a'\n";

        assertThat(Diagnostics.parse(Language.PYTHON, output))
                .containsExactly(new Diagnostic(3, null, Severity.ERROR, "KeyError: 'a'"));
    }

    @Test
    void python_framesOnlyInOtherFiles_isEmpty() {
        String output = "Traceback (most recent call last):\n  File \"/usr/lib/python3.11/x.py\", line 3, in f\nKeyError: 'a'\n";

        assertThat(Diagnostics.parse(Language.PYTHON, output)).isEmpty();
    }

    // ---- shared behaviour ----

    @Test
    void noLineNumber_isEmpty() {
        assertThat(Diagnostics.parse(Language.C, "gcc: fatal error: no input files")).isEmpty();
        assertThat(Diagnostics.parse(Language.JAVA, "error: file not found: Main.java")).isEmpty();
        assertThat(Diagnostics.parse(Language.PYTHON, "ValueError: invalid literal")).isEmpty();
        assertThat(Diagnostics.parse(Language.PYTHON, "boom")).isEmpty();
    }

    @Test
    void garbageNullBlankAndHugeNumbers_neverThrow() {
        for (Language language : Language.values()) {
            assertThat(Diagnostics.parse(language, null)).isEmpty();
            assertThat(Diagnostics.parse(language, "")).isEmpty();
            assertThat(Diagnostics.parse(language, "   \n\t ")).isEmpty();
            assertThat(Diagnostics.parse(language, "\u0000￿:::\r\n^^^\n  File \"")).isEmpty();
            assertThat(Diagnostics.parse(language, "main.c:99999999999999:1: error: x\n"
                    + "Main.java:99999999999999: error: x\n  File \"main.py\", line 99999999999999\nE: x")).isEmpty();
            assertThat(Diagnostics.parse(language, "main.c:0:0: error: zero\nMain.java:0: error: zero")).isEmpty();
        }
        assertThat(Diagnostics.parse(null, "main.c:1:1: error: x")).isEmpty();
    }

    @Test
    void crlfOutputIsParsed() {
        assertThat(Diagnostics.parse(Language.C, "main.c:1:2: error: boom\r\n    1 | x\r\n"))
                .containsExactly(new Diagnostic(1, 2, Severity.ERROR, "boom"));
    }

    @Test
    void sameLineAndMessage_isDeduplicated() {
        String output = "main.c:3:1: error: dup\nmain.c:3:9: error: dup\nmain.c:3:1: error: other\nmain.c:4:1: error: dup\n";

        assertThat(Diagnostics.parse(Language.C, output)).extracting(Diagnostic::line, Diagnostic::message)
                .containsExactly(tuple(3, "dup"), tuple(3, "other"), tuple(4, "dup"));
    }

    @Test
    void atMostFiftyDiagnostics() {
        StringBuilder output = new StringBuilder();
        IntStream.rangeClosed(1, 80)
                .forEach(i -> output.append("main.c:").append(i).append(":1: error: e").append(i).append('\n'));

        List<Diagnostic> result = Diagnostics.parse(Language.C, output.toString());

        assertThat(result).hasSize(Diagnostics.MAX_DIAGNOSTICS);
        assertThat(result.get(0).line()).isEqualTo(1);
        assertThat(result.get(49).line()).isEqualTo(50);
    }

    @Test
    void overlongMessageIsCapped() {
        List<Diagnostic> result = Diagnostics.parse(Language.C, "main.c:1:1: error: " + "x".repeat(5000));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).message()).hasSize(Diagnostics.MAX_MESSAGE_LENGTH + 3).endsWith("...");
    }
}
