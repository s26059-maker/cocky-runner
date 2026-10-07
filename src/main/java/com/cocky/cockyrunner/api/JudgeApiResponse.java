package com.cocky.cockyrunner.api;

public record JudgeApiResponse(
        ApiVerdict verdict,
        int passedCount,
        int totalCount,
        long maxTimeMs,
        Long maxMemoryKb,
        String compileOutput
) {
}
