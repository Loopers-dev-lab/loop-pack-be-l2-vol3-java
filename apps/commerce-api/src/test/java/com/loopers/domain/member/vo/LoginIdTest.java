package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LoginIdTest {

    @DisplayName("LoginId를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("영문+숫자만 있으면 정상 생성된다")
        @Test
        void success_whenAlphanumeric() {
            LoginId loginId = assertDoesNotThrow(() -> new LoginId("testuser123"));
            assertThat(loginId.value()).isEqualTo("testuser123");
        }

        @DisplayName("빈 값이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenEmpty() {
            assertThatThrownBy(() -> new LoginId(""))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenNull() {
            assertThatThrownBy(() -> new LoginId(null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("특수문자가 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenContainsSpecialCharacter() {
            assertThatThrownBy(() -> new LoginId("test@user"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("한글이 포함되면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenContainsKorean() {
            assertThatThrownBy(() -> new LoginId("test유저"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
