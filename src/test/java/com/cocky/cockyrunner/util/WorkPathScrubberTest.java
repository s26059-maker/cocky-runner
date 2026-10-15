package com.cocky.cockyrunner.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkPathScrubberTest {

    @Test
    void stripsWorkPrefixFromEveryOccurrence() {
        String raw = "  File \"/work/main.py\", line 7, in <module>\n  File \"/work/main.py\", line 5, in g\n";

        assertThat(WorkPathScrubber.scrub(raw))
                .isEqualTo("  File \"main.py\", line 7, in <module>\n  File \"main.py\", line 5, in g\n");
    }

    @Test
    void leavesOtherTextAndNullAlone() {
        assertThat(WorkPathScrubber.scrub("main.c:1:1: error: x")).isEqualTo("main.c:1:1: error: x");
        assertThat(WorkPathScrubber.scrub("/workspace/x /work")).isEqualTo("/workspace/x /work");
        assertThat(WorkPathScrubber.scrub(null)).isNull();
    }

    @Test
    void scrubAndTruncateAppliesBoth() {
        String raw = "/work/main.py" + "x".repeat(TextTruncator.MAX_LENGTH);

        String result = WorkPathScrubber.scrubAndTruncate(raw);

        assertThat(result).startsWith("main.pyxxx").endsWith("... (truncated)").doesNotContain("/work/");
    }
}
