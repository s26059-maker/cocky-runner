package com.cocky.cockyrunner.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunnerApiPropertiesTest {

    @Test
    void blankOrMissingToken_failsStartup() {
        assertThatThrownBy(() -> new RunnerApiProperties("", 4)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RunnerApiProperties("  ", 4)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RunnerApiProperties(null, 4)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nonPositiveConcurrency_failsStartup() {
        assertThatThrownBy(() -> new RunnerApiProperties("t", 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validValues() {
        assertThat(new RunnerApiProperties("t", 4).maxConcurrent()).isEqualTo(4);
    }
}
