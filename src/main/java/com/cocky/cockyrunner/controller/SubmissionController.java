package com.cocky.cockyrunner.controller;

import com.cocky.cockyrunner.domain.JudgeResult;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.Verdict;
import com.cocky.cockyrunner.dto.SubmissionRequest;
import com.cocky.cockyrunner.dto.SubmissionResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import com.cocky.cockyrunner.service.JudgeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/problems")
public class SubmissionController {

    private final JudgeService judgeService;

    public SubmissionController(JudgeService judgeService) {
        this.judgeService = judgeService;
    }

    @PostMapping("/{id}/submissions")
    public ResponseEntity<SubmissionResponse> submit(@PathVariable String id, @RequestBody SubmissionRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new InvalidExecutionRequestException("code must not be blank");
        }
        Language language = parseLanguage(request.language());

        JudgeResult result = judgeService.judge(id, language, request.code());

        SubmissionResponse response = new SubmissionResponse(
                result.verdict(),
                result.passedCount(),
                result.totalCount(),
                result.failedCaseNumber(),
                result.maxExecutionTimeMs(),
                result.errorOutput()
        );

        HttpStatus httpStatus = result.verdict() == Verdict.ERROR
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(response);
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
