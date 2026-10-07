package com.cocky.cockyrunner.api;

public record RunRequest(
        String language,
        String sourceCode,
        String stdin,
        Integer timeLimitMs,
        Long memoryLimitKb
) {
}
