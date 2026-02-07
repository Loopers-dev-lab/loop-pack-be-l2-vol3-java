package com.loopers.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MaskingUtilsTest {

    @DisplayName("마지막 글자를 *로 마스킹한다.")
    @Test
    void maskLastCharacter_success() {
        String result = MaskingUtils.maskLastCharacter("김용권");

        assertThat(result).isEqualTo("김용*");
    }

    @DisplayName("1글자인 경우 *만 반환한다.")
    @Test
    void maskLastCharacter_singleCharacter() {
        String result = MaskingUtils.maskLastCharacter("김");

        assertThat(result).isEqualTo("*");
    }

    @DisplayName("빈 문자열이면 빈 문자열을 반환한다.")
    @Test
    void maskLastCharacter_emptyString() {
        String result = MaskingUtils.maskLastCharacter("");

        assertThat(result).isEqualTo("");
    }

    @DisplayName("null이면 null을 반환한다.")
    @Test
    void maskLastCharacter_null() {
        String result = MaskingUtils.maskLastCharacter(null);

        assertThat(result).isNull();
    }
}
