package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserTest {
    @DisplayName("회원 가입시, ")
    @Nested
    class LoginIdCreate {
        @DisplayName("loginId가 영문/숫자만 포함하면 정상적으로 생성된다.")
        @Test
        void createsUser_whenLoginIdIsAlphanumeric() {
            // arrange
            String loginId = "uniqueTester123";

            // act
            User user = UserFixture.builder()
                                   .loginId(loginId)
                                   .build();
            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isNotNull()
            );
        }

        @DisplayName("loginId에 영문/숫자 외의 문자가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdIsNotAlphanumeric() {
            // arrange
            String loginId = "invalidId#?*";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserFixture.builder()
                           .loginId(loginId)
                           .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
