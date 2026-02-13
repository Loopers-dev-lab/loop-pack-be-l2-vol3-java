package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NameMaskingPolicyTest {

    @DisplayName("null 이름을 마스킹하면 예외가 발생한다.")
    @Test
    void mask_withNull_shouldThrowException() {
        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            NameMaskingPolicy.mask(null);
        });
    }

    @DisplayName("빈 문자열 이름을 마스킹하면 예외가 발생한다.")
    @Test
    void mask_withEmptyString_shouldThrowException() {
        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            NameMaskingPolicy.mask("");
        });
    }

    @DisplayName("공백만 있는 이름을 마스킹하면 예외가 발생한다.")
    @Test
    void mask_withWhitespace_shouldThrowException() {
        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            NameMaskingPolicy.mask("   ");
        });
    }

    @DisplayName("한 글자 이름을 마스킹하면 예외가 발생한다.")
    @Test
    void mask_withSingleCharacter_shouldThrowException() {
        // when & then
        assertThrows(IllegalArgumentException.class, () -> {
            NameMaskingPolicy.mask("김");
        });
    }

    @DisplayName("두 글자 이름은 마지막 글자가 마스킹된다.")
    @Test
    void mask_withTwoCharacters_shouldMaskLastCharacter() {
        // when
        String masked = NameMaskingPolicy.mask("김철");

        // then
        assertThat(masked).isEqualTo("김*");
    }

    @DisplayName("세 글자 이상 이름은 마지막 글자만 마스킹된다.")
    @Test
    void mask_withThreeOrMoreCharacters_shouldMaskLastCharacter() {
        // when
        String masked = NameMaskingPolicy.mask("홍길동");

        // then
        assertThat(masked).isEqualTo("홍길*");
    }

    @DisplayName("영문 이름도 마지막 글자가 마스킹된다.")
    @Test
    void mask_withEnglishName_shouldMaskLastCharacter() {
        // when
        String masked1 = NameMaskingPolicy.mask("Alan");
        String masked2 = NameMaskingPolicy.mask("Jo");

        // then
        assertThat(masked1).isEqualTo("Ala*");
        assertThat(masked2).isEqualTo("J*");
    }
}
