package com.loopers.domain.user.vo;

import com.loopers.domain.user.exception.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

public class NameTest {

    @Nested
    @DisplayName("이름 형식 검증")
    class NameValidationTest {

        @ParameterizedTest
        @DisplayName("유효한 이름 - 한글만")
        @ValueSource(strings = {"홍길동", "김", "박철수", "이순신"})
        void validKoreanName(String name) {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Name(name));
        }

        @ParameterizedTest
        @DisplayName("유효한 이름 - 영문만")
        @ValueSource(strings = {"John", "Jane", "Kim", "A"})
        void validEnglishName(String name) {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Name(name));
        }

        @ParameterizedTest
        @DisplayName("이름 형식 오류 - 빈 문자열 또는 null")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void emptyOrNullName(String name) {
            // when & then
            assertThatThrownBy(() -> new Name(name))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("이름 형식 오류 - 한글/영문 혼용")
        @ValueSource(strings = {"홍길동John", "John홍길동", "김John", "Hong길동"})
        void mixedName(String name) {
            // when & then
            assertThatThrownBy(() -> new Name(name))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("이름 형식 오류 - 숫자 포함")
        @ValueSource(strings = {"홍길동1", "John123", "123"})
        void nameWithNumbers(String name) {
            // when & then
            assertThatThrownBy(() -> new Name(name))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("이름 형식 오류 - 특수문자 포함")
        @ValueSource(strings = {"홍길동!", "John@", "김-철수", "Jane.Doe"})
        void nameWithSpecialChars(String name) {
            // when & then
            assertThatThrownBy(() -> new Name(name))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("이름 형식 오류 - 공백 포함")
        @ValueSource(strings = {"홍 길동", "John Doe", "Kim Cheol Su"})
        void nameWithSpaces(String name) {
            // when & then
            assertThatThrownBy(() -> new Name(name))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("한글 이름 길이 경계값 - 4자 성공")
        void koreanNameExactly4Chars() {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Name("홍길동님"));
        }

        @Test
        @DisplayName("한글 이름 형식 오류 - 4자 초과")
        void koreanNameExceeds4Chars() {
            // when & then
            assertThatThrownBy(() -> new Name("홍길동님이"))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("영문 이름 길이 경계값 - 50자 성공")
        void englishNameExactly50Chars() {
            // given
            String name = "a".repeat(50);

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Name(name));
        }

        @Test
        @DisplayName("영문 이름 형식 오류 - 50자 초과")
        void englishNameExceeds50Chars() {
            // given
            String name = "a".repeat(51);

            // when & then
            assertThatThrownBy(() -> new Name(name))
                    .isInstanceOf(UserValidationException.class);
        }
    }
}
