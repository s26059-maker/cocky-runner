package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.domain.Problem;
import com.cocky.cockyrunner.domain.TestCase;
import com.cocky.cockyrunner.dto.ProblemDetailResponse;
import com.cocky.cockyrunner.dto.ProblemSummaryResponse;
import com.cocky.cockyrunner.dto.TestCaseResponse;
import com.cocky.cockyrunner.exception.ProblemNotFoundException;
import com.cocky.cockyrunner.repository.ProblemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;

    public ProblemService(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    public List<ProblemSummaryResponse> getProblems() {
        return problemRepository.findAll().stream()
                .map(problem -> new ProblemSummaryResponse(problem.id(), problem.title()))
                .toList();
    }

    public ProblemDetailResponse getProblem(String id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException("problem not found: " + id));

        List<TestCaseResponse> sampleTestCases = problem.testCases().stream()
                .filter(TestCase::sample)
                .map(testCase -> new TestCaseResponse(testCase.input(), testCase.expectedOutput()))
                .toList();

        return new ProblemDetailResponse(
                problem.id(),
                problem.title(),
                problem.description(),
                problem.timeLimitMs(),
                sampleTestCases
        );
    }
}