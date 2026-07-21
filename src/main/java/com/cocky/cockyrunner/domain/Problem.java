package com.cocky.cockyrunner.domain;

import java.util.List;

public record Problem(
        String id,
        String title,
        String description,
        int timeLimitMs,
        List<TestCase> testCases
) {
}