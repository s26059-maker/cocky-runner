package com.cocky.cockyrunner.domain;

import java.util.List;

/**
 * All the per-language configuration the judge needs: which image to run, what
 * to name the source file, how to compile it (if at all), how to run it, and how
 * much slack to give it on the wall-clock time limit.
 *
 * @param dockerImage          image used for both the compile and run containers
 * @param sourceFileName       name the submitted code is written to on disk, e.g. "main.c"
 * @param compileCommand       command run inside the compile container; null or empty
 *                             for languages that need no compile step (e.g. Python)
 * @param runCommand           command run inside the execution container
 * @param timeLimitMultiplier  multiplier applied to a problem's timeLimitMs for this language
 */
public record LanguageSpec(
        String dockerImage,
        String sourceFileName,
        List<String> compileCommand,
        List<String> runCommand,
        double timeLimitMultiplier
) {
    public boolean needsCompile() {
        return compileCommand != null && !compileCommand.isEmpty();
    }
}
