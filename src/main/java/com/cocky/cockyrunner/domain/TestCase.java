package com.cocky.cockyrunner.domain;

public record TestCase(
        String input,
        String expectedOutput,
        boolean sample
) {
}