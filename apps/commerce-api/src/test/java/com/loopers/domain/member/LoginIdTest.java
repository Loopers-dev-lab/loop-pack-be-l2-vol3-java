package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginIdTest {

    @DisplayName("LoginId 생성")
    @Nested
    class Create {

        @DisplayName("null, 빈 문자열, 공백은 허용하지 않는다")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rejectsBlankValues(String value) {
            // given & when
            CoreException result = assertThrows(CoreException.class, () -> new LoginId(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("특수문자나 한글이 포함되면 생성할 수 없다")
        @ParameterizedTest
        @ValueSource(strings = {"test@user", "test유저", "hello world!", "user#1"})
        void rejectsInvalidCharacters(String value) {
            // given & when
            CoreException result = assertThrows(CoreException.class, () -> new LoginId(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("영문, 숫자 조합으로 생성할 수 있다")
        @ParameterizedTest
        @ValueSource(strings = {"testuser", "12345", "user123"})
        void acceptsAlphanumericValues(String value) {
            // given & when
            LoginId loginId = new LoginId(value);

            // then
            assertThat(loginId.value()).isEqualTo(value);
        }
    }

    @DisplayName("동등성 비교")
    @Nested
    class Equals {

        @DisplayName("같은 값이면 동일하다")
        @Test
        void sameValueMeansEqual() {
            // given
            LoginId one = new LoginId("kwonmo");
            LoginId another = new LoginId("kwonmo");

            // when & then
            assertThat(one).isEqualTo(another);
            assertThat(one.hashCode()).isEqualTo(another.hashCode());
        }

        @DisplayName("다른 값이면 다르다")
        @Test
        void differentValueMeansNotEqual() {
            // given
            LoginId one = new LoginId("kwonmo");
            LoginId another = new LoginId("jihun");

            // when & then
            assertThat(one).isNotEqualTo(another);
        }
    }
}
