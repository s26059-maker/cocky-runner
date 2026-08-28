package com.cocky.cockyrunner.config;

import com.cocky.cockyrunner.domain.Language;
import com.cocky.cockyrunner.domain.LanguageSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageSpecRegistryTest {

    private static DockerProperties propertiesWithImages(Map<String, String> images) {
        return new DockerProperties(images, 5, "256m", 1.0, 64, 65536, "build/judge-work-test");
    }

    @Test
    void get_returnsConfiguredSpecForEachLanguage() {
        LanguageSpecRegistry registry = new LanguageSpecRegistry(
                propertiesWithImages(Map.of("c", "gcc:14", "python", "python:3.11-slim")));

        LanguageSpec c = registry.get(Language.C);
        assertThat(c.dockerImage()).isEqualTo("gcc:14");
        assertThat(c.sourceFileName()).isEqualTo("main.c");
        assertThat(c.needsCompile()).isTrue();
        assertThat(c.timeLimitMultiplier()).isEqualTo(1.0);

        LanguageSpec python = registry.get(Language.PYTHON);
        assertThat(python.dockerImage()).isEqualTo("python:3.11-slim");
        assertThat(python.sourceFileName()).isEqualTo("main.py");
        assertThat(python.needsCompile()).isFalse();
        assertThat(python.timeLimitMultiplier()).isEqualTo(3.0);
    }

    @Test
    void constructor_throwsWhenImageMissingForALanguage() {
        // "c" is intentionally absent.
        DockerProperties properties = propertiesWithImages(Map.of("python", "python:3.11-slim"));

        assertThatThrownBy(() -> new LanguageSpecRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("C");
    }

    @Test
    void constructor_throwsWhenImagesMapIsNull() {
        DockerProperties properties = propertiesWithImages(null);

        assertThatThrownBy(() -> new LanguageSpecRegistry(properties))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_throwsWhenImageIsBlank() {
        DockerProperties properties = propertiesWithImages(Map.of("c", "  ", "python", "python:3.11-slim"));

        assertThatThrownBy(() -> new LanguageSpecRegistry(properties))
                .isInstanceOf(IllegalStateException.class);
    }
}
