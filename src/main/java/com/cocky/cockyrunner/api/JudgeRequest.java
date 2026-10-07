package com.cocky.cockyrunner.api;

import java.util.List;

public record JudgeRequest(
        String language,
        String sourceCode,
        Integer timeLimitMs,
        Long memoryLimitKb,
        List<TestCaseInput> testCases
) {
    public record TestCaseInput(String input, String expectedOutput) {
    }
}
