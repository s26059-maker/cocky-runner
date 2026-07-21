package com.cocky.cockyrunner.service;

import com.cocky.cockyrunner.domain.JudgeResult;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.Problem;
import com.cocky.cockyrunner.domain.TestCase;
import com.cocky.cockyrunner.domain.Verdict;
import com.cocky.cockyrunner.dto.ExecutionRequest;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.exception.ProblemNotFoundException;
import com.cocky.cockyrunner.repository.ProblemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs a submission's test cases in order, stopping at the first non-AC result.
 */
@Service
public class JudgeService {

    private final ProblemRepository problemRepository;
    private final ExecutionService executionService;
    private final OutputComparator outputComparator = new OutputComparator();

    public JudgeService(ProblemRepository problemRepository, ExecutionService executionService) {
        this.problemRepository = problemRepository;
        this.executionService = executionService;
    }

    public JudgeResult judge(String problemId, Language language, String code) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException("problem not found: " + problemId));

        List<TestCase> testCases = problem.testCases();
        int passedCount = 0;
        long maxExecutionTimeMs = 0;

        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            ExecutionRequest request = new ExecutionRequest(language.name(), code, testCase.input());
            ExecutionResponse response = executionService.execute(request, problem.timeLimitMs());

            maxExecutionTimeMs = Math.max(maxExecutionTimeMs, response.executionTimeMs());

            Verdict caseVerdict = judgeCase(testCase, response);
            if (caseVerdict != Verdict.AC) {
                return new JudgeResult(caseVerdict, passedCount, testCases.size(), i + 1, maxExecutionTimeMs);
            }
            passedCount++;
        }

        return new JudgeResult(Verdict.AC, passedCount, testCases.size(), null, maxExecutionTimeMs);
    }

    private Verdict judgeCase(TestCase testCase, ExecutionResponse response) {
        return switch (response.status()) {
            case TIMEOUT -> Verdict.TLE;
            case RUNTIME_ERROR -> Verdict.RE;
            case ERROR -> Verdict.ERROR;
            case SUCCESS -> outputComparator.match(testCase.expectedOutput(), response.stdout())
                    ? Verdict.AC
                    : Verdict.WA;
        };
    }
}
