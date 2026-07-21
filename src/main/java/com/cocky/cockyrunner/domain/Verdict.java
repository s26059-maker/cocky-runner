package com.cocky.cockyrunner.domain;

/**
 * Final judgement for a submission. New values (e.g. CE for compile error,
 * MLE for memory limit exceeded) can be added here as the judge grows.
 */
public enum Verdict {
    AC,
    WA,
    TLE,
    RE,
    ERROR
}
