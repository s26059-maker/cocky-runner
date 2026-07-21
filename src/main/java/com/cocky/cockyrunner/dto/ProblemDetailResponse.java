package com.cocky.cockyrunner.dto;

import java.util.List;

public record ProblemDetailResponse(
        String id,
        String title,
        String description,
        int timeLimitMs,
        List<TestCaseResponse> sampleTestCases
) {
}