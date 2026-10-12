package com.cocky.cockyrunner.api;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageDockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.runner.DockerRunner;
import com.cocky.cockyrunner.service.ExecutionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Sandbox restrictions and verdicts through the real Docker path. Run via {@code ./gradlew dockerTest}. */
@Tag("docker")
class SandboxHardeningDockerTest {

    @TempDir
    Path workDirRoot;

    private ExternalExecutionService newService() {
        DockerProperties props = new DockerProperties(
                Map.of(
                        Language.C, new LanguageDockerProperties("gcc:14", 1500),
                        Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 1500),
                        Language.JAVA, new LanguageDockerProperties("eclipse-temurin:21-jdk", 1500)),
                5, "256m", 1.0, 64, 65536, workDirRoot.toString());
        LanguageSpecRegistry registry = new LanguageSpecRegistry(props);
        return new ExternalExecutionService(new ExecutionService(new DockerRunner(props), registry, props),
                registry, new RunnerApiProperties("t", 4));
    }

    private RunApiResponse run(String language, String code, int timeLimitMs, long memKb) {
        return newService().run(new RunRequest(language, code, "", timeLimitMs, memKb));
    }

    private RunApiResponse runPy(String code) {
        return run("python", code, 8000, 262144);
    }

    @Test
    void runsAsNonRootWithoutCapabilities() {
        RunApiResponse r = runPy("""
                import os
                print(os.getuid(), os.getgid())
                for l in open('/proc/self/status'):
                    if l.startswith(('CapEff', 'NoNewPrivs')): print(l.strip())
                """);
        assertThat(r.status()).isEqualTo(RunStatus.OK);
        assertThat(r.stdout()).contains("10001 10001").contains("CapEff:\t0000000000000000").contains("NoNewPrivs:\t1");
    }

    @Test
    void rootFilesystemIsReadOnly() {
        RunApiResponse py = runPy("open('/evil.txt', 'w').write('x')\n");
        assertThat(py.status()).isEqualTo(RunStatus.RE);
        assertThat(py.stderr()).containsAnyOf("Read-only", "Permission denied");

        RunApiResponse c = run("c", """
                #include <stdio.h>
                int main(){ FILE*f=fopen("/etc/evil","w"); puts(f?"WROTE":"DENIED"); return 0; }
                """, 3000, 262144);
        assertThat(c.stdout()).isEqualTo("DENIED\n");
    }

    @Test
    void workspaceFileBomb_isKilledAndCleanedUp() throws IOException {
        RunApiResponse r = runPy("""
                i = 0
                while True:
                    with open('/work/f%d' % i, 'wb') as f:
                        f.write(b'x' * 8_000_000)
                    i += 1
                """);
        assertThat(r.status()).isEqualTo(RunStatus.RE);
        try (Stream<Path> left = Files.list(workDirRoot)) {
            assertThat(left).isEmpty();
        }
    }

    @Test
    void singleHugeFile_isRefused() {
        RunApiResponse r = runPy("open('/work/big', 'wb').write(b'x' * 100_000_000)\n");
        assertThat(r.status()).isEqualTo(RunStatus.RE);
    }

    @Test
    void tmpIsSizeLimited() {
        RunApiResponse r = runPy("""
                for i in range(5):
                    open('/tmp/big%d' % i, 'wb').write(b'x' * 15_000_000)
                """);
        assertThat(r.status()).isNotEqualTo(RunStatus.OK);
    }

    @Test
    void cVerdicts() {
        assertThat(run("c", "int main(){int*p=0;*p=1;return 0;}\n", 3000, 262144).status()).isEqualTo(RunStatus.RE);
        assertThat(run("c", "int main(){for(;;){}}\n", 1000, 262144).status()).isEqualTo(RunStatus.TLE);
        assertThat(run("c", """
                #include <stdlib.h>
                #include <string.h>
                int main(){for(;;){char*p=malloc(10000000);if(p)memset(p,1,10000000);}}
                """, 5000, 65536).status()).isEqualTo(RunStatus.MLE);
    }

    @Test
    void pythonVerdicts() {
        assertThat(runPy("raise SystemExit(3)\n").status()).isEqualTo(RunStatus.RE);
        assertThat(run("python", "while True: pass\n", 1000, 262144).status()).isEqualTo(RunStatus.TLE);
        assertThat(run("python", "x=[]\nwhile True: x.append(bytearray(10**7))\n", 5000, 65536).status())
                .isEqualTo(RunStatus.MLE);
    }

    @Test
    void javaVerdicts() {
        assertThat(run("java", "public class Main{public static void main(String[] a){throw new RuntimeException();}}\n",
                3000, 524288).status()).isEqualTo(RunStatus.RE);
        assertThat(run("java", "public class Main{public static void main(String[] a){while(true){}}}\n",
                1000, 524288).status()).isEqualTo(RunStatus.TLE);
        RunApiResponse ok = run("java", "public class Main{public static void main(String[] a){System.out.println(42);}}\n",
                3000, 524288);
        assertThat(ok.status()).isEqualTo(RunStatus.OK);
        assertThat(ok.stdout()).isEqualTo("42\n");
    }
}
