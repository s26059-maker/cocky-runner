package com.cocky.cockyrunner.controller;

import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.dto.SampleRunResponse;
import com.cocky.cockyrunner.dto.SubmissionRequest;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import com.cocky.cockyrunner.service.JudgeService;
import com.cocky.cockyrunner.util.SourceCodeLimits;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!prod") // local-dev frontend only; Cocky uses /internal/**
@RestController
@RequestMapping("/api/v1/problems")
public class SampleRunController {

    private final JudgeService judgeService;

    public SampleRunController(JudgeService judgeService) {
        this.judgeService = judgeService;
    }

    @PostMapping("/{id}/samples/run")
    public SampleRunResponse runSamples(@PathVariable String id, @RequestBody SubmissionRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new InvalidExecutionRequestException("code must not be blank");
        }
        SourceCodeLimits.validateLength(request.code());
        Language language = parseLanguage(request.language());

        return SampleRunResponse.from(judgeService.judgeSamples(id, language, request.code()));
    }

    private Language parseLanguage(String language) {
        if (language == null || language.isBlank()) {
            throw new InvalidExecutionRequestException("language must not be blank");
        }
        try {
            return Language.valueOf(language.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidExecutionRequestException("unsupported language: " + language);
        }
    }
}
