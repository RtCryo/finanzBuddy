package com.finanzbuddy.parser.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorMessageTruncatorTest {

    @Test
    void truncatesByCodePointWithoutBreakingSurrogatePairs() {
        String result = ErrorMessageTruncator.truncate(new RuntimeException("😀😀😀"), 20);

        assertThat(result).isEqualTo("RuntimeException: 😀😀");
        assertThat(result.codePointCount(0, result.length())).isEqualTo(20);
    }
}
