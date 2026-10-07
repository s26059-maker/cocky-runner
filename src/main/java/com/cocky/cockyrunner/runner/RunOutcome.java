package com.cocky.cockyrunner.runner;

import com.cocky.cockyrunner.dto.ExecutionResponse;

/**
 * One run's response plus what the plain {@link ExecutionResponse} can't carry.
 *
 * @param oomKilled       Docker reported the container as OOM-killed (false when that couldn't be determined)
 * @param stdoutTruncated stdout exceeded the capture cap, so {@code response.stdout()} is incomplete
 */
public record RunOutcome(ExecutionResponse response, boolean oomKilled, boolean stdoutTruncated) {

    public RunOutcome(ExecutionResponse response, boolean oomKilled) {
        this(response, oomKilled, false);
    }
}
