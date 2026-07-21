package com.cocky.cockyrunner.dto;

public record TestCaseResponse(
        String input,
        String expectedOutput
) {
}