package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameMaskerTest {

    private final NameMasker masker = new NameMasker();

    @DisplayName("이름 마스킹")
    @Nested
    class Mask {

        @DisplayName("이름의 마지막 글자가 *로 변환된다")
        @Test
        void masksLastCharacterWithAsterisk() {
            // arrange
            String name = "홍길동";

            // act
            String result = masker.mask(name);

            // assert
            assertThat(result).isEqualTo("홍길*");
        }

        @DisplayName("영문 이름의 마지막 글자가 *로 변환된다")
        @Test
        void masksLastCharacterOfEnglishName() {
            // arrange
            String name = "John";

            // act
            String result = masker.mask(name);

            // assert
            assertThat(result).isEqualTo("Joh*");
        }

        @DisplayName("두 글자 이름의 마지막 글자가 *로 변환된다")
        @Test
        void masksLastCharacterOfTwoCharacterName() {
            // arrange
            String name = "길동";

            // act
            String result = masker.mask(name);

            // assert
            assertThat(result).isEqualTo("길*");
        }

        @DisplayName("한 글자 이름은 *로 변환된다")
        @Test
        void masksSingleCharacterName() {
            // arrange
            String name = "동";

            // act
            String result = masker.mask(name);

            // assert
            assertThat(result).isEqualTo("*");
        }
    }
}
