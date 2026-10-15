package com.cocky.cockyrunner.dto;

import com.cocky.cockyrunner.diagnostic.Diagnostic;
import com.cocky.cockyrunner.domain.SampleRunResult;
import com.cocky.cockyrunner.domain.Verdict;

import java.util.List;

public record SampleRunResponse(
        String compileError,
        List<SampleCaseResponse> cases,
        List<Diagnostic> diagnostics
) {

    public record SampleCaseResponse(
            int index,
            String input,
            String expectedOutput,
            String actualOutput,
            Verdict verdict,
            String stderr,
            Long userWallMs
    ) {
    }

    public static SampleRunResponse from(SampleRunResult result) {
        List<SampleCaseResponse> cases = result.cases().stream()
                .map(c -> new SampleCaseResponse(c.index(), c.input(), c.expectedOutput(), c.actualOutput(),
                        c.verdict(), c.stderr(), c.userWallMs()))
                .toList();
        return new SampleRunResponse(result.compileError(), cases, result.diagnostics());
    }
}
