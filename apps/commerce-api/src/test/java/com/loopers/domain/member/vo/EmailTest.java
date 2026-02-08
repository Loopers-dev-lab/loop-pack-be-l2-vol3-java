package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class EmailTest {

    @DisplayName("Email을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 이메일 포맷이면 정상 생성된다")
        @Test
        void success_whenValidFormat() {
            Email email = assertDoesNotThrow(() -> new Email("test@example.com"));
            assertThat(email.value()).isEqualTo("test@example.com");
        }

        @DisplayName("빈 값이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenEmpty() {
            assertThatThrownBy(() -> new Email(""))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenNull() {
            assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("@가 없으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenNoAtSign() {
            assertThatThrownBy(() -> new Email("testexample.com"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("도메인이 없으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenNoDomain() {
            assertThatThrownBy(() -> new Email("test@"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
