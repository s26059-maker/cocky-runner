package com.cocky.cockyrunner.domain;

public enum ExecutionStatus {
    SUCCESS,
    RUNTIME_ERROR,
    TIMEOUT,
    /** The submitted code failed to compile; distinct from RUNTIME_ERROR since the cause differs. */
    COMPILE_ERROR,
    ERROR
}
