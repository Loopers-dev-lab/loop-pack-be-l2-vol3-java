package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberNameTest {

    @DisplayName("MemberName 생성")
    @Nested
    class Create {

        @DisplayName("null, 빈 문자열, 공백은 허용하지 않는다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rejectsBlankValues(String value) {
            // given & when
            CoreException result = assertThrows(CoreException.class, () -> new MemberName(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_NAME);
        }

        @DisplayName("유효한 이름으로 생성할 수 있다")
        @Test
        void validNameCreatesSuccessfully() {
            // given
            String value = "양권모";

            // when
            MemberName name = new MemberName(value);

            // then
            assertThat(name.value()).isEqualTo(value);
        }
    }

    @DisplayName("이름 마스킹")
    @Nested
    class Masked {

        @DisplayName("글자 수에 따라 마지막 글자를 마스킹한다")
        @ParameterizedTest
        @CsvSource({"양, *", "양권, 양*", "양권모, 양권*"})
        void masksLastCharacter(String input, String expected) {
            // given
            MemberName name = new MemberName(input);

            // when
            String result = name.masked();

            // then
            assertThat(result).isEqualTo(expected);
        }
    }

    @DisplayName("동등성 비교")
    @Nested
    class Equals {

        @DisplayName("같은 값이면 동일하다")
        @Test
        void sameValueMeansEqual() {
            // given
            MemberName one = new MemberName("양권모");
            MemberName another = new MemberName("양권모");

            // when & then
            assertThat(one).isEqualTo(another);
            assertThat(one.hashCode()).isEqualTo(another.hashCode());
        }

        @DisplayName("다른 값이면 다르다")
        @Test
        void differentValueMeansNotEqual() {
            // given
            MemberName one = new MemberName("양권모");
            MemberName another = new MemberName("박지훈");

            // when & then
            assertThat(one).isNotEqualTo(another);
        }
    }
}
