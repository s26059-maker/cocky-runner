package com.cocky.cockyrunner.runner;

import com.cocky.cockyrunner.config.DockerProperties;
import com.cocky.cockyrunner.config.LanguageDockerProperties;
import com.cocky.cockyrunner.config.LanguageSpecRegistry;
import com.cocky.cockyrunner.domain.ExecutionStatus;
import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import com.cocky.cockyrunner.dto.ExecutionResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A running user program must not be able to change the workspace (source, binary,
 * run.sh), create anything under /timing, or remove/replace the one mounted
 * /timing/.timing file; /tmp and that file's contents are all it can write.
 * Needs Docker: {@code ./gradlew dockerTest}.
 */
@Tag("docker")
class WorkspaceIsolationDockerTest {

    @TempDir
    Path workDirRoot;

    private DockerRunner newRunner() {
        return new DockerRunner(dockerProperties());
    }

    private DockerProperties dockerProperties() {
        return new DockerProperties(
                Map.of(
                        Language.C, new LanguageDockerProperties("gcc:14", 1000),
                        Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 1000),
                        Language.JAVA, new LanguageDockerProperties("eclipse-temurin:21-jdk", 2000)),
                5, "256m", 1.0, 64, 65536, workDirRoot.toString());
    }

    private LanguageSpec spec(Language language) {
        return new LanguageSpecRegistry(dockerProperties()).get(language);
    }

    private static final String PY_WORK_ATTACKER = """
            import sys
            if sys.stdin.read().strip() == 'attack':
                for path in ('/work/new.txt', '/work/main.py', '/work/run.sh'):
                    try:
                        open(path, 'w').write('pwned')
                        print('WROTE', path)
                    except OSError:
                        print('DENIED', path)
            else:
                print('ok')
            """;

    @Test
    void python_cannotCreateOrOverwriteWorkspaceFiles_andNextCaseIsGradedNormally() {
        SubmissionExecution execution = newRunner().prepareSubmission(spec(Language.PYTHON), PY_WORK_ATTACKER);
        try {
            ExecutionResponse attack = execution.run("attack", 5000L);
            assertThat(attack.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(attack.stdout()).isEqualTo(
                    "DENIED /work/new.txt\nDENIED /work/main.py\nDENIED /work/run.sh\n");

            ExecutionResponse next = execution.run("normal", 5000L);
            assertThat(next.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(next.stdout()).isEqualTo("ok\n");
            assertThat(next.userWallMs()).isNotNull();
        } finally {
            execution.close();
        }
    }

    @Test
    void c_cannotReplaceOrDeleteTheBinary_andNextCaseIsGradedNormally() {
        String code = """
                #include <stdio.h>
                #include <unistd.h>
                int main(void){
                  char b[16] = {0};
                  if (!fgets(b, sizeof b, stdin)) return 1;
                  if (b[0] == 'a') {
                    puts(unlink("/work/main") == 0 ? "UNLINKED" : "DENIED_UNLINK");
                    puts(rename("/work/main", "/work/moved") == 0 ? "RENAMED" : "DENIED_RENAME");
                    FILE *f = fopen("/work/evil", "w");
                    puts(f ? "WROTE" : "DENIED_CREATE");
                  } else {
                    puts("ok");
                  }
                  return 0;
                }
                """;
        SubmissionExecution execution = newRunner().prepareSubmission(spec(Language.C), code);
        try {
            ExecutionResponse attack = execution.run("attack\n", 5000L);
            assertThat(attack.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(attack.stdout()).isEqualTo("DENIED_UNLINK\nDENIED_RENAME\nDENIED_CREATE\n");

            ExecutionResponse next = execution.run("normal\n", 5000L);
            assertThat(next.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(next.stdout()).isEqualTo("ok\n");
        } finally {
            execution.close();
        }
    }

    @Test
    void python_cannotCreateAnythingUnderTiming_norUnlinkOrRenameTheTimingFile() {
        String code = """
                import os, sys
                def attempt(name, fn):
                    try:
                        fn()
                        print('WROTE', name)
                    except OSError:
                        print('DENIED', name)
                if sys.stdin.read().strip() == 'attack':
                    attempt('file', lambda: open('/timing/new', 'w').write('x'))
                    attempt('mkdir', lambda: os.mkdir('/timing/d'))
                    attempt('symlink', lambda: os.symlink('/etc/passwd', '/timing/l'))
                    attempt('link', lambda: os.link('/timing/.timing', '/timing/hl'))
                    attempt('unlink', lambda: os.unlink('/timing/.timing'))
                    attempt('rename', lambda: os.rename('/timing/.timing', '/timing/moved'))
                else:
                    print('ok')
                """;
        SubmissionExecution execution = newRunner().prepareSubmission(spec(Language.PYTHON), code);
        try {
            ExecutionResponse attack = execution.run("attack", 5000L);
            assertThat(attack.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(attack.stdout()).isEqualTo(
                    "DENIED file\nDENIED mkdir\nDENIED symlink\nDENIED link\nDENIED unlink\nDENIED rename\n");
            // the timing file survived the attempts, so the wrapper could still report
            assertThat(attack.userWallMs()).isNotNull();

            ExecutionResponse next = execution.run("normal", 5000L);
            assertThat(next.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(next.stdout()).isEqualTo("ok\n");
            assertThat(next.userWallMs()).isNotNull();
        } finally {
            execution.close();
        }
    }

    @Test
    void java_runsFromReadOnlyWorkspace() {
        String code = "public class Main{public static void main(String[] a){System.out.println(42);}}\n";
        SubmissionExecution execution = newRunner().prepareSubmission(spec(Language.JAVA), code);
        try {
            ExecutionResponse r = execution.run("", 8000L);
            assertThat(r.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(r.stdout()).isEqualTo("42\n");
            assertThat(r.userWallMs()).isNotNull();
        } finally {
            execution.close();
        }
    }

    @Test
    void python_noBytecodeWriteAttemptsOrErrors_andTimingStillReported() {
        String code = "import json, collections\nprint('fine')\n";
        SubmissionExecution execution = newRunner().prepareSubmission(spec(Language.PYTHON), code);
        try {
            ExecutionResponse r = execution.run("", 5000L);
            assertThat(r.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(r.stdout()).isEqualTo("fine\n");
            assertThat(r.stderr()).isEmpty();
            assertThat(r.userWallMs()).isNotNull();
            assertThat(r.userCpuMs()).isNotNull();
        } finally {
            execution.close();
        }
    }

    @Test
    void forgedTimingContent_isDisplayOnly_nullsInsteadOfException_andNextCaseIsClean() {
        String code = """
                import sys
                if sys.stdin.read().strip() == 'forge':
                    open('/timing/.timing', 'w').write('START 99\\nEND 1\\nBEFORE\\nAFTER\\n\\xff garbage')
                print('done')
                """;
        SubmissionExecution execution = newRunner().prepareSubmission(spec(Language.PYTHON), code);
        try {
            ExecutionResponse forged = execution.run("forge", 5000L);
            assertThat(forged.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(forged.userWallMs()).isNull();
            assertThat(forged.userCpuMs()).isNull();

            // the forged content was truncated away before the next case
            ExecutionResponse next = execution.run("honest", 5000L);
            assertThat(next.status()).isEqualTo(ExecutionStatus.SUCCESS);
            assertThat(next.userWallMs()).isNotNull();
            assertThat(next.userCpuMs()).isNotNull();
        } finally {
            execution.close();
        }
    }

    @Test
    void close_removesWorkspaceAndTimingDirectory() throws IOException {
        SubmissionExecution execution = newRunner().prepareSubmission(spec(Language.PYTHON), "print('ok')\n");
        try {
            assertThat(execution.run("", 5000L).status()).isEqualTo(ExecutionStatus.SUCCESS);
            try (Stream<Path> entries = Files.list(workDirRoot)) {
                assertThat(entries).hasSize(2); // <uuid> and <uuid>-timing
            }
        } finally {
            execution.close();
        }
        try (Stream<Path> left = Files.list(workDirRoot)) {
            assertThat(left).isEmpty();
        }
    }
}
