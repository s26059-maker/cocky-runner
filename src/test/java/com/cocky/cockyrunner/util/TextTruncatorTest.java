package com.cocky.cockyrunner.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTruncatorTest {

    @Test
    void textWithinLimit_isReturnedUnchanged() {
        String text = "x".repeat(TextTruncator.MAX_LENGTH);

        assertThat(TextTruncator.truncate(text)).isEqualTo(text);
    }

    @Test
    void textAtExactLimit_isReturnedUnchanged() {
        String text = "y".repeat(TextTruncator.MAX_LENGTH);

        assertThat(TextTruncator.truncate(text)).isSameAs(text);
    }

    @Test
    void textExceedingLimit_isTruncatedWithMarker() {
        String text = "x".repeat(TextTruncator.MAX_LENGTH + 500);

        String result = TextTruncator.truncate(text);

        assertThat(result).hasSizeLessThan(text.length());
        assertThat(result).startsWith("x".repeat(TextTruncator.MAX_LENGTH));
        assertThat(result).contains("truncated");
    }

    @Test
    void nullText_returnsNull() {
        assertThat(TextTruncator.truncate(null)).isNull();
    }
}
