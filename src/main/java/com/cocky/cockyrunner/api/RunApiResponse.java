package com.cocky.cockyrunner.api;

public record RunApiResponse(
        RunStatus status,
        String stdout,
        String stderr,
        String compileOutput,
        long timeMs
) {
}
