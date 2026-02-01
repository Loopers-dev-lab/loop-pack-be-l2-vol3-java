package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class UserTest {

    @DisplayName("User를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllValuesAreValid() {
            // arrange & act & assert
            assertThatCode(() -> new User("user123", "Password1!", "홍길동", "1990-01-01", "test@email.com"))
                    .doesNotThrowAnyException();
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsBirthDate() {
            // arrange
            String password = "Pass19900101!";
            String birthDate = "1990-01-01";

            // act & assert
            assertThatThrownBy(() -> new User("user123", password, "홍길동", birthDate, "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}