package com.cocky.cockyrunner.controller;

import com.cocky.cockyrunner.dto.ProblemDetailResponse;
import com.cocky.cockyrunner.dto.ProblemSummaryResponse;
import com.cocky.cockyrunner.service.ProblemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Profile("!prod") // local-dev frontend only; Cocky uses /internal/**
@RestController
@RequestMapping("/api/v1/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public List<ProblemSummaryResponse> getProblems() {
        return problemService.getProblems();
    }

    @GetMapping("/{id}")
    public ProblemDetailResponse getProblem(@PathVariable String id) {
        return problemService.getProblem(id);
    }
}