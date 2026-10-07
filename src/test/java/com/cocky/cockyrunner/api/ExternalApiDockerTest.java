package com.cocky.cockyrunner.api;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageDockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.runner.DockerRunner;
import com.cocky.cockyrunner.service.ExecutionService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end for the external API core against real Docker. Run via
 * {@code ./gradlew dockerTest} (needs gcc:14, python:3.11-slim, eclipse-temurin:21-jdk pulled).
 */
@Tag("docker")
class ExternalApiDockerTest {

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

    private JudgeApiResponse judge(String language, String code, int timeLimitMs, long memKb) {
        return newService().judge(new JudgeRequest(language, code, timeLimitMs, memKb, List.of(
                new JudgeRequest.TestCaseInput("1 2\n", "3\n"),
                new JudgeRequest.TestCaseInput("5 7\n", "12\n"))));
    }

    private static final String PY_AC = "a,b=map(int,input().split())\nprint(a+b)\n";
    private static final String PY_WA = "a,b=map(int,input().split())\nprint(a+b+1)\n";
    private static final String PY_TLE = "while True: pass\n";
    private static final String C_AC =
            "#include <stdio.h>\nint main(){int a,b;scanf(\"%d %d\",&a,&b);printf(\"%d\\n\",a+b);return 0;}\n";
    private static final String C_WA =
            "#include <stdio.h>\nint main(){int a,b;scanf(\"%d %d\",&a,&b);printf(\"%d\\n\",a+b+1);return 0;}\n";
    private static final String C_TLE = "int main(){for(;;){}}\n";
    private static final String JAVA_AC = "import java.util.*;\npublic class Main{public static void main(String[] x){"
            + "Scanner s=new Scanner(System.in);System.out.println(s.nextInt()+s.nextInt());}}\n";
    private static final String JAVA_WA = JAVA_AC.replace("s.nextInt()+s.nextInt()", "s.nextInt()+s.nextInt()+1");
    private static final String JAVA_TLE = "public class Main{public static void main(String[] x){while(true){}}}\n";

    @Test
    void python_ac_wa_ce_tle() {
        assertThat(judge("python", PY_AC, 2000, 262144).verdict()).isEqualTo(ApiVerdict.AC);
        JudgeApiResponse wa = judge("python", PY_WA, 2000, 262144);
        assertThat(wa.verdict()).isEqualTo(ApiVerdict.WA);
        assertThat(wa.passedCount()).isZero();
        assertThat(judge("python", PY_TLE, 1000, 262144).verdict()).isEqualTo(ApiVerdict.TLE);
        // python has no compile step: a syntax error surfaces as a runtime error
        assertThat(judge("python", "print(", 2000, 262144).verdict()).isEqualTo(ApiVerdict.RE);
    }

    @Test
    void c_ac_wa_ce_tle() {
        assertThat(judge("c", C_AC, 2000, 262144).verdict()).isEqualTo(ApiVerdict.AC);
        assertThat(judge("c", C_WA, 2000, 262144).verdict()).isEqualTo(ApiVerdict.WA);
        assertThat(judge("c", C_TLE, 1000, 262144).verdict()).isEqualTo(ApiVerdict.TLE);
        JudgeApiResponse ce = judge("c", "int main( {", 2000, 262144);
        assertThat(ce.verdict()).isEqualTo(ApiVerdict.CE);
        assertThat(ce.compileOutput()).isNotBlank();
    }

    @Test
    void java_ac_wa_ce_tle() {
        assertThat(judge("java", JAVA_AC, 3000, 524288).verdict()).isEqualTo(ApiVerdict.AC);
        assertThat(judge("java", JAVA_WA, 3000, 524288).verdict()).isEqualTo(ApiVerdict.WA);
        assertThat(judge("java", JAVA_TLE, 1000, 524288).verdict()).isEqualTo(ApiVerdict.TLE);
        assertThat(judge("java", "public class Main {", 3000, 524288).verdict()).isEqualTo(ApiVerdict.CE);
    }

    @Test
    void memoryHog_isMle() {
        String code = "x = []\nwhile True:\n    x.append(bytearray(10**7))\n";
        RunApiResponse response = newService().run(new RunRequest("python", code, "", 5000, 65536L));
        assertThat(response.status()).isEqualTo(RunStatus.MLE);
    }

    @Test
    void run_returnsStdout() {
        RunApiResponse response = newService().run(new RunRequest("python", PY_AC, "1 2\n", 2000, 262144L));
        assertThat(response.status()).isEqualTo(RunStatus.OK);
        assertThat(response.stdout()).isEqualTo("3\n");
    }

    @Test
    void largeExpectedOutput_100Kb_isAc_andOver1MbIsWa() {
        String code = "print('line\\n' * 20000, end='')\n";
        String expected = "line\n".repeat(20_000); // 100KB
        JudgeApiResponse ac = newService().judge(new JudgeRequest("python", code, 3000, 262144L,
                List.of(new JudgeRequest.TestCaseInput("", expected))));
        assertThat(ac.verdict()).isEqualTo(ApiVerdict.AC);

        String huge = "print('x' * 2_000_000)\n";
        JudgeApiResponse wa = newService().judge(new JudgeRequest("python", huge, 3000, 262144L,
                List.of(new JudgeRequest.TestCaseInput("", "x"))));
        assertThat(wa.verdict()).isEqualTo(ApiVerdict.WA);
    }

    @Test
    void run_stdoutIsCappedTo64Kb() {
        RunApiResponse response = newService().run(new RunRequest("python", "print('x' * 200000)\n", "", 3000, 262144L));
        assertThat(response.status()).isEqualTo(RunStatus.OK);
        assertThat(response.stdout().length()).isEqualTo(64 * 1024);
    }

    @Test
    void noContainersAreLeftBehind_afterAcTleAndMle() throws Exception {
        ExternalExecutionService service = newService();
        service.run(new RunRequest("python", "print(1)", "", 2000, 262144L));
        service.run(new RunRequest("python", "while True: pass", "", 500, 262144L));
        service.run(new RunRequest("python", "x=[]\nwhile True: x.append(bytearray(10**7))", "", 5000, 65536L));

        Process ps = new ProcessBuilder("docker", "ps", "-a", "-q", "--filter", "name=run-").start();
        String leftovers = new String(ps.getInputStream().readAllBytes()).trim();
        ps.waitFor();
        assertThat(leftovers).isEmpty();
    }
}
