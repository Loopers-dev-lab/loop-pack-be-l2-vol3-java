package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @DisplayName("Email 생성")
    @Nested
    class Create {

        @DisplayName("유효한 이메일로 생성할 수 있다")
        @Test
        void validEmailCreatesSuccessfully() {
            // given
            String value = "kwonmo@example.com";

            // when
            Email email = new Email(value);

            // then
            assertThat(email.value()).isEqualTo(value);
        }

        @DisplayName("이메일 형식이 올바르지 않으면 생성할 수 없다")
        @ParameterizedTest
        @ValueSource(strings = {"", "testexample.com", "test@", "@example.com"})
        void rejectsInvalidEmailFormats(String value) {
            // given & when
            CoreException result = assertThrows(CoreException.class, () -> new Email(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_EMAIL);
        }
    }

    @DisplayName("동등성 비교")
    @Nested
    class Equals {

        @DisplayName("같은 값이면 동일하다")
        @Test
        void sameValueMeansEqual() {
            // given
            Email one = new Email("kwonmo@example.com");
            Email another = new Email("kwonmo@example.com");

            // when & then
            assertThat(one).isEqualTo(another);
            assertThat(one.hashCode()).isEqualTo(another.hashCode());
        }

        @DisplayName("다른 값이면 다르다")
        @Test
        void differentValueMeansNotEqual() {
            // given
            Email one = new Email("kwonmo@example.com");
            Email another = new Email("jihun@example.com");

            // when & then
            assertThat(one).isNotEqualTo(another);
        }
    }
}
