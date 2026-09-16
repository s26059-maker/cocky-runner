package com.cocky.cockyrunner.config;

import com.cocky.cockyrunner.domain.Language;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the {@code Map<Language, LanguageDockerProperties>} binding itself, separately
 * from {@link LanguageSpecRegistryTest} (which exercises {@link LanguageSpecRegistry}'s
 * own validation against hand-built {@link DockerProperties} instances, not real property
 * binding). Uses {@link ApplicationContextRunner} rather than a full {@code @SpringBootTest}
 * so this stays independent of the real {@code application.yml} and needs no Docker daemon.
 */
class DockerPropertiesTest {

    @EnableConfigurationProperties(DockerProperties.class)
    static class TestConfig {
    }

    private static final String[] BASE_PROPERTIES = {
            "runner.docker.timeout-seconds=5",
            "runner.docker.memory=256m",
            "runner.docker.cpus=1.0",
            "runner.docker.pids-limit=64",
            "runner.docker.max-output-bytes=65536",
            "runner.docker.work-dir=build/judge-work-test"
    };

    @Test
    void lowercaseLanguageKeys_bindToTheLanguageEnum() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(BASE_PROPERTIES)
                .withPropertyValues(
                        "runner.docker.languages.c.image=gcc:14",
                        "runner.docker.languages.c.startup-budget-ms=1000",
                        "runner.docker.languages.python.image=python:3.11-slim",
                        "runner.docker.languages.python.startup-budget-ms=1000"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DockerProperties properties = context.getBean(DockerProperties.class);
                    assertThat(properties.languages()).containsOnlyKeys(Language.C, Language.PYTHON);
                    assertThat(properties.languages().get(Language.C).image()).isEqualTo("gcc:14");
                    assertThat(properties.languages().get(Language.C).startupBudgetMs()).isEqualTo(1000L);
                    assertThat(properties.languages().get(Language.PYTHON).image()).isEqualTo("python:3.11-slim");
                });
    }

    /**
     * Documents (rather than fixes) what actually happens for an unknown language key -
     * e.g. a typo like "pyhton". See the task report for the observed outcome and the
     * reasoning on why it was left as-is rather than hardened against.
     */
    @Test
    void unknownLanguageKey_failsStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(BASE_PROPERTIES)
                .withPropertyValues(
                        "runner.docker.languages.c.image=gcc:14",
                        "runner.docker.languages.c.startup-budget-ms=1000",
                        "runner.docker.languages.pyhton.image=python:3.11-slim",
                        "runner.docker.languages.pyhton.startup-budget-ms=1000"
                )
                .run(context -> assertThat(context).hasFailed());
    }
}
