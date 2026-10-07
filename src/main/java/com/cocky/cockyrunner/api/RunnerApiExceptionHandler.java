package com.cocky.cockyrunner.api;

import com.cocky.cockyrunner.dto.ErrorResponse;
import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Scoped to the external controller so the frontend API's error handling is untouched. */
@RestControllerAdvice(assignableTypes = InternalApiController.class)
public class RunnerApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RunnerApiExceptionHandler.class);

    @ExceptionHandler(InvalidExecutionRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidExecutionRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RunnerBusyException.class)
    public ResponseEntity<ErrorResponse> handleBusy(RunnerBusyException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RunnerInternalException.class)
    public ResponseEntity<ErrorResponse> handleInternal(RunnerInternalException ex) {
        log.error("internal runner failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("internal runner error"));
    }
}
