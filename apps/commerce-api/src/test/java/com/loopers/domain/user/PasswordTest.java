package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordTest {

    @DisplayName("Password를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("암호화된 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenEncryptedValueProvided() {
            // act
            Password password = new Password("$2a$10$encryptedValue");

            // assert
            assertThat(password.getEncryptedValue()).isEqualTo("$2a$10$encryptedValue");
        }

        @DisplayName("null이면, CoreException이 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // act & assert
            assertThatThrownBy(() -> new Password(null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("빈 문자열이면, CoreException이 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsBlank() {
            // act & assert
            assertThatThrownBy(() -> new Password(""))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("동등성을 비교할 때, ")
    @Nested
    class Equality {

        @DisplayName("같은 값이면 동일한 객체로 판단한다.")
        @Test
        void isEqual_whenValuesAreSame() {
            // arrange
            Password password1 = new Password("encrypted1");
            Password password2 = new Password("encrypted1");

            // assert
            assertThat(password1).isEqualTo(password2);
            assertThat(password1.hashCode()).isEqualTo(password2.hashCode());
        }

        @DisplayName("다른 값이면 다른 객체로 판단한다.")
        @Test
        void isNotEqual_whenValuesAreDifferent() {
            // arrange
            Password password1 = new Password("encrypted1");
            Password password2 = new Password("encrypted2");

            // assert
            assertThat(password1).isNotEqualTo(password2);
        }
    }
}
