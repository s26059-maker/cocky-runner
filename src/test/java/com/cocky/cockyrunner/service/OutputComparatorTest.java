package com.cocky.cockyrunner.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTest {

    private final OutputComparator comparator = new OutputComparator();

    @Test
    void identicalOutputMatches() {
        assertThat(comparator.match("3\n", "3\n")).isTrue();
    }

    @Test
    void missingTrailingNewlineMatches() {
        assertThat(comparator.match("3\n", "3")).isTrue();
    }

    @Test
    void trailingWhitespaceOnLineIsIgnored() {
        assertThat(comparator.match("a b\n", "a b   \n")).isTrue();
    }

    @Test
    void crlfIsNormalizedToLf() {
        assertThat(comparator.match("a\r\nb", "a\nb")).isTrue();
    }

    @Test
    void trailingBlankLinesAreIgnored() {
        assertThat(comparator.match("a\nb", "a\nb\n\n\n")).isTrue();
        assertThat(comparator.match("a\nb\n\n", "a\nb")).isTrue();
        assertThat(comparator.match("a\n  \n", "a")).isTrue();
    }

    @Test
    void interiorBlankLinesAreSignificant() {
        assertThat(comparator.match("a\n\nb", "a\nb")).isFalse();
    }

    @Test
    void crlfWithTrailingSpacesAndBlankLinesCombined() {
        assertThat(comparator.match("a\nb", "a  \r\nb \r\n\r\n")).isTrue();
    }

    @Test
    void leadingWhitespaceIsSignificant() {
        assertThat(comparator.match("  a", "a")).isFalse();
    }

    @Test
    void differingContentDoesNotMatch() {
        assertThat(comparator.match("1\n2", "1\n3")).isFalse();
    }
}
