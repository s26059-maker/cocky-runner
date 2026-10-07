package com.cocky.cockyrunner.api;

/** No execution slot was immediately available (maps to 429). */
public class RunnerBusyException extends RuntimeException {
    public RunnerBusyException(String message) {
        super(message);
    }
}
