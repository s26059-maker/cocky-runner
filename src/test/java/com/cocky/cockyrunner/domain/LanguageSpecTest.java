package com.cocky.cockyrunner.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageSpecTest {

    @Test
    void mutatingTheListPassedToTheConstructor_doesNotAffectTheSpec() {
        List<String> mutableCompileCommand = new ArrayList<>(List.of("gcc", "-o", "main", "main.c"));
        List<String> mutableRunCommand = new ArrayList<>(List.of("./main"));

        LanguageSpec spec = new LanguageSpec("gcc:14", "main.c", mutableCompileCommand, mutableRunCommand, 1.0);

        mutableCompileCommand.add("--extra-flag");
        mutableRunCommand.add("--extra-arg");

        assertThat(spec.compileCommand()).containsExactly("gcc", "-o", "main", "main.c");
        assertThat(spec.runCommand()).containsExactly("./main");
    }

    @Test
    void accessorLists_areThemselvesUnmodifiable() {
        LanguageSpec spec = new LanguageSpec("gcc:14", "main.c", List.of("gcc"), List.of("./main"), 1.0);

        assertThatThrownBy(() -> spec.compileCommand().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spec.runCommand().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullCompileCommand_isNormalizedToEmptyList() {
        LanguageSpec spec = new LanguageSpec("python:3.11-slim", "main.py", null, List.of("python3", "main.py"), 3.0);

        assertThat(spec.compileCommand()).isEmpty();
        assertThat(spec.needsCompile()).isFalse();
    }

    @Test
    void nonEmptyCompileCommand_needsCompileIsTrue() {
        LanguageSpec spec = new LanguageSpec("gcc:14", "main.c", List.of("gcc", "main.c"), List.of("./main"), 1.0);

        assertThat(spec.needsCompile()).isTrue();
    }

    @Test
    void resolvedTimeoutMs_appliesMultiplierAndRounds() {
        LanguageSpec spec = new LanguageSpec("python:3.11-slim", "main.py", null, List.of("python3", "main.py"), 3.0);

        assertThat(spec.resolvedTimeoutMs(2000)).isEqualTo(6000L);
    }
}
