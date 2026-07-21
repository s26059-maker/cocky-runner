package com.cocky.cockyrunner.dto;

import com.cocky.cockyrunner.domain.ExecutionStatus;

public record ExecutionResponse(
        ExecutionStatus status,
        String stdout,
        String stderr,
        int exitCode,
        long executionTimeMs
) {
}