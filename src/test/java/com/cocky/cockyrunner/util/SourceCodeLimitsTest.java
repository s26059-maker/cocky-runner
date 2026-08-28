package com.cocky.cockyrunner.util;

import com.cocky.cockyrunner.exception.InvalidExecutionRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceCodeLimitsTest {

    @Test
    void exactlyAtLimit_passes() {
        String code = "x".repeat(SourceCodeLimits.MAX_SOURCE_LENGTH);

        assertThatCode(() -> SourceCodeLimits.validateLength(code)).doesNotThrowAnyException();
    }

    @Test
    void oneOverLimit_throws() {
        String code = "x".repeat(SourceCodeLimits.MAX_SOURCE_LENGTH + 1);

        assertThatThrownBy(() -> SourceCodeLimits.validateLength(code))
                .isInstanceOf(InvalidExecutionRequestException.class);
    }

    @Test
    void emptyString_passes() {
        assertThatCode(() -> SourceCodeLimits.validateLength("")).doesNotThrowAnyException();
    }

    @Test
    void nullCode_passes() {
        // Not this validator's job - the caller's own blank-code check rejects null.
        assertThatCode(() -> SourceCodeLimits.validateLength(null)).doesNotThrowAnyException();
    }

    @Test
    void surrogatePairString_isCountedByUtf16CodeUnitsNotCodePoints() {
        String emoji = "😀"; // 😀 - one code point, two UTF-16 code units (a surrogate pair)

        // Exactly MAX_SOURCE_LENGTH code points - at, not over, a code-point-based limit -
        // but MAX_SOURCE_LENGTH * 2 UTF-16 code units, well over the actual limit. If
        // validateLength() counted code points (e.g. via codePointCount()) instead of
        // code units (String.length()), this would incorrectly pass.
        String code = emoji.repeat(SourceCodeLimits.MAX_SOURCE_LENGTH);
        assertThat(code.length()).isEqualTo(SourceCodeLimits.MAX_SOURCE_LENGTH * 2);
        assertThat(code.codePointCount(0, code.length())).isEqualTo(SourceCodeLimits.MAX_SOURCE_LENGTH);

        assertThatThrownBy(() -> SourceCodeLimits.validateLength(code))
                .isInstanceOf(InvalidExecutionRequestException.class);
    }

    @Test
    void surrogatePairString_atExactCodeUnitLimit_passes() {
        String emoji = "😀"; // 2 UTF-16 code units per code point
        // MAX_SOURCE_LENGTH / 2 surrogate pairs => exactly MAX_SOURCE_LENGTH code units.
        String code = emoji.repeat(SourceCodeLimits.MAX_SOURCE_LENGTH / 2);
        assertThat(code.length()).isEqualTo(SourceCodeLimits.MAX_SOURCE_LENGTH);

        assertThatCode(() -> SourceCodeLimits.validateLength(code)).doesNotThrowAnyException();
    }
}
