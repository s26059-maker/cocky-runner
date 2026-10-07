package com.cocky.cockyrunner.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** External API for the Cocky backend. Token-protected by {@link RunnerTokenFilter}. */
@RestController
@RequestMapping("/internal/v1")
public class InternalApiController {

    private final ExternalExecutionService service;

    public InternalApiController(ExternalExecutionService service) {
        this.service = service;
    }

    @PostMapping("/judge")
    public JudgeApiResponse judge(@RequestBody JudgeRequest request) {
        return service.judge(request);
    }

    @PostMapping("/run")
    public RunApiResponse run(@RequestBody RunRequest request) {
        return service.run(request);
    }
}
