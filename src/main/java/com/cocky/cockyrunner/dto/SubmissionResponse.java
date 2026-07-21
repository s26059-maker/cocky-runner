package com.cocky.cockyrunner.dto;

import com.cocky.cockyrunner.domain.Verdict;

public record SubmissionResponse(
        Verdict verdict,
        int passedCount,
        int totalCount,
        Integer failedCaseNumber,
        long maxExecutionTimeMs
) {
}
