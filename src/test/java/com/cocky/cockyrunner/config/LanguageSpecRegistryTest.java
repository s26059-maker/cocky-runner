package com.cocky.cockyrunner.config;

import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageSpecRegistryTest {

    private static DockerProperties propertiesWithLanguages(Map<Language, LanguageDockerProperties> languages) {
        return new DockerProperties(languages, 5, "256m", 1.0, 64, 65536, "build/judge-work-test");
    }

    private static Map<Language, LanguageDockerProperties> validLanguages() {
        return Map.of(
                Language.C, new LanguageDockerProperties("gcc:14", 1000),
                Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 1000)
        );
    }

    @Test
    void get_returnsConfiguredSpecForEachLanguage() {
        LanguageSpecRegistry registry = new LanguageSpecRegistry(propertiesWithLanguages(validLanguages()));

        LanguageSpec c = registry.get(Language.C);
        assertThat(c.dockerImage()).isEqualTo("gcc:14");
        assertThat(c.sourceFileName()).isEqualTo("main.c");
        assertThat(c.needsCompile()).isTrue();
        assertThat(c.timeLimitMultiplier()).isEqualTo(1.0);
        assertThat(c.startupBudgetMs()).isEqualTo(1000L);

        LanguageSpec python = registry.get(Language.PYTHON);
        assertThat(python.dockerImage()).isEqualTo("python:3.11-slim");
        assertThat(python.sourceFileName()).isEqualTo("main.py");
        assertThat(python.needsCompile()).isFalse();
        assertThat(python.timeLimitMultiplier()).isEqualTo(3.0);
        assertThat(python.startupBudgetMs()).isEqualTo(1000L);
    }

    @Test
    void get_usesEachLanguagesOwnConfiguredStartupBudget() {
        Map<Language, LanguageDockerProperties> languages = Map.of(
                Language.C, new LanguageDockerProperties("gcc:14", 200),
                Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 5000)
        );

        LanguageSpecRegistry registry = new LanguageSpecRegistry(propertiesWithLanguages(languages));

        assertThat(registry.get(Language.C).startupBudgetMs()).isEqualTo(200L);
        assertThat(registry.get(Language.PYTHON).startupBudgetMs()).isEqualTo(5000L);
    }

    @Test
    void constructor_throwsWhenALanguageIsMissingEntirely() {
        // "C" is intentionally absent.
        DockerProperties properties = propertiesWithLanguages(
                Map.of(Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 1000)));

        assertThatThrownBy(() -> new LanguageSpecRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("C")
                .hasMessageContaining("runner.docker.languages.c");
    }

    @Test
    void constructor_throwsWhenLanguagesMapIsNull() {
        DockerProperties properties = propertiesWithLanguages(null);

        assertThatThrownBy(() -> new LanguageSpecRegistry(properties))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_throwsWhenImageIsBlank() {
        Map<Language, LanguageDockerProperties> languages = Map.of(
                Language.C, new LanguageDockerProperties("  ", 1000),
                Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 1000)
        );
        DockerProperties properties = propertiesWithLanguages(languages);

        assertThatThrownBy(() -> new LanguageSpecRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runner.docker.languages.c.image");
    }

    @Test
    void constructor_throwsWhenStartupBudgetIsZero() {
        // long defaults to 0 when the yml key is simply absent, so this also covers
        // "missing" - the error message names both possibilities (see below).
        Map<Language, LanguageDockerProperties> languages = Map.of(
                Language.C, new LanguageDockerProperties("gcc:14", 0),
                Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", 1000)
        );
        DockerProperties properties = propertiesWithLanguages(languages);

        assertThatThrownBy(() -> new LanguageSpecRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runner.docker.languages.c.startup-budget-ms")
                .hasMessageContaining("missing or not positive")
                .hasMessageContaining("0");
    }

    @Test
    void constructor_throwsWhenStartupBudgetIsNegative() {
        Map<Language, LanguageDockerProperties> languages = Map.of(
                Language.C, new LanguageDockerProperties("gcc:14", 1000),
                Language.PYTHON, new LanguageDockerProperties("python:3.11-slim", -1)
        );
        DockerProperties properties = propertiesWithLanguages(languages);

        assertThatThrownBy(() -> new LanguageSpecRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("runner.docker.languages.python.startup-budget-ms")
                .hasMessageContaining("-1");
    }
}
