package com.loopers.support.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingUtilTest {

    @DisplayName("마지막 N자 마스킹")
    @Nested
    class MaskLast {

        @DisplayName("마지막 1자를 마스킹한다")
        @Test
        void masksLastOneCharacter() {
            // arrange
            String value = "홍길동";

            // act
            String result = MaskingUtil.maskLast(value, 1);

            // assert
            assertThat(result).isEqualTo("홍길*");
        }

        @DisplayName("마지막 2자를 마스킹한다")
        @Test
        void masksLastTwoCharacters() {
            // arrange
            String value = "홍길동";

            // act
            String result = MaskingUtil.maskLast(value, 2);

            // assert
            assertThat(result).isEqualTo("홍**");
        }

        @DisplayName("문자열 길이보다 큰 수를 마스킹하면 전부 마스킹된다")
        @Test
        void masksAllWhenCountExceedsLength() {
            // arrange
            String value = "홍길동";

            // act
            String result = MaskingUtil.maskLast(value, 5);

            // assert
            assertThat(result).isEqualTo("***");
        }

        @DisplayName("빈 문자열이면 빈 문자열을 반환한다")
        @Test
        void returnsEmptyWhenValueIsEmpty() {
            // arrange
            String value = "";

            // act
            String result = MaskingUtil.maskLast(value, 1);

            // assert
            assertThat(result).isEqualTo("");
        }

        @DisplayName("null이면 null을 반환한다")
        @Test
        void returnsNullWhenValueIsNull() {
            // act
            String result = MaskingUtil.maskLast(null, 1);

            // assert
            assertThat(result).isNull();
        }
    }

    @DisplayName("이메일 마스킹")
    @Nested
    class MaskEmail {

        @DisplayName("@ 앞부분 중 앞 2자만 남기고 마스킹한다")
        @Test
        void masksEmailLocalPart() {
            // arrange
            String email = "testuser@example.com";

            // act
            String result = MaskingUtil.maskEmail(email);

            // assert
            assertThat(result).isEqualTo("te******@example.com");
        }

        @DisplayName("@ 앞부분이 2자 이하면 마스킹하지 않는다")
        @Test
        void doesNotMaskWhenLocalPartIsTooShort() {
            // arrange
            String email = "te@example.com";

            // act
            String result = MaskingUtil.maskEmail(email);

            // assert
            assertThat(result).isEqualTo("te@example.com");
        }

        @DisplayName("null이면 null을 반환한다")
        @Test
        void returnsNullWhenEmailIsNull() {
            // act
            String result = MaskingUtil.maskEmail(null);

            // assert
            assertThat(result).isNull();
        }
    }
}
