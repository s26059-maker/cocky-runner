package com.cocky.cockyrunner.api;

/** Runner infrastructure failed (docker down, workspace error, ...). Maps to 500, never to a verdict. */
public class RunnerInternalException extends RuntimeException {
    public RunnerInternalException(String message) {
        super(message);
    }
}
