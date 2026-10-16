package com.cocky.cockyrunner.domain;

import com.cocky.cockyrunner.diagnostic.Diagnostic;

import java.util.List;

/**
 * Per-case outcome of running only a problem's public sample cases. Unlike
 * {@link JudgeResult} it never stops at the first failure and exposes input /
 * expected / actual output - safe because only {@code sample == true} cases are
 * ever included.
 *
 * @param compileError the (truncated) compiler output when compilation failed,
 *                     in which case {@code cases} is empty; null otherwise
 * @param diagnostics  parsed from the raw compile error, or else from the first
 *                     RE case's stderr; empty when there is none
 */
public record SampleRunResult(
        String compileError,
        List<SampleCaseResult> cases,
        List<Diagnostic> diagnostics
) {

    public SampleRunResult {
        diagnostics = diagnostics == null ? List.of() : diagnostics;
    }

    public SampleRunResult(String compileError, List<SampleCaseResult> cases) {
        this(compileError, cases, List.of());
    }

    /**
     * @param index        1-based position among the problem's sample cases
     * @param actualOutput truncated stdout; null when the case produced none to show
     * @param stderr       truncated stderr; null when blank
     * @param userWallMs   the user program's own wall time, null when unmeasured
     */
    public record SampleCaseResult(
            int index,
            String input,
            String expectedOutput,
            String actualOutput,
            Verdict verdict,
            String stderr,
            Long userWallMs
    ) {
    }
}
