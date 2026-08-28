# Known issues

## Execution time is wall-clock, not CPU time

`ExecutionResponse.executionTimeMs()` (surfaced to clients as
`JudgeResult.maxExecutionTimeMs()`) is measured in
`DockerRunner.run()`/`DockerRunner.compile()` as wall-clock time around the
`docker run` process:

```java
long start = System.currentTimeMillis();
Process process = new ProcessBuilder(command).start();
...
long elapsed = System.currentTimeMillis() - start;
```

This is **not** the CPU/user time of the submitted program. It includes:

- `docker run` process startup (JVM `ProcessBuilder.start()` + the `docker`
  CLI itself spinning up)
- container creation (cgroup/namespace setup) each time, since every run uses
  a fresh `--rm` container rather than a warm/reused one
- scheduling noise from whatever else is running on the host and the Docker
  daemon at that moment

Consequences:

- Measured times are systematically inflated versus the program's actual
  running time, by an amount that isn't fixed - it varies with host load and
  Docker daemon state.
- Two submissions with identical algorithmic cost can report different
  `executionTimeMs()` run to run. This is tolerable for the current TLE
  cutoff (which already has slack via `LanguageSpec.timeLimitMultiplier()`),
  but the number is not reliable for anything stricter - e.g. ranking
  submissions by speed, or a tight performance-grading mode.

This was known and explicitly deferred (see "CPU 시간 기반 측정" in the
scope-exclusion list of the C-language-support work) rather than fixed as
part of adding C support. Fixing it properly would mean measuring time
*inside* the container (e.g. wrapping the run command with `/usr/bin/time`
and parsing its output, or reading cgroup CPU accounting) instead of around
the host-side `docker run` process.
