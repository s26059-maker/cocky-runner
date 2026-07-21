package com.cocky.cockyrunner.controller;

import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.dto.ExecutionRequest;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import com.cocky.cockyrunner.service.ExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping
    public ResponseEntity<ExecutionResponse> execute(@RequestBody ExecutionRequest request) {
        ExecutionResponse response = executionService.execute(request);
        HttpStatus httpStatus = response.status() == ExecutionStatus.ERROR
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(response);
    }
}
