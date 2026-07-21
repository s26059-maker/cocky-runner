package com.cocky.cockyrunner.dto;

public record ExecutionRequest(
        String language,
        String code,
        String stdin
) {
}