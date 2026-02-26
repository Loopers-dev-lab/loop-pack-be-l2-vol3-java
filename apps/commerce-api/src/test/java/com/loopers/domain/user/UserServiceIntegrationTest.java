package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class UserServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @DisplayName("회원가입을 할 때,")
    @Nested
    class Register {

        @DisplayName("유효한 정보를 입력하면, 회원이 DB에 저장된다.")
        @Test
        void savesUserToDatabase_whenValidInputProvided() {
            // arrange
            String loginId = "user123";
            String password = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "test@email.com";

            // act
            User result = userService.register(loginId, password, name, birthDate, email);

            // assert
            assertAll(
                    () -> assertThat(result.getId()).isNotNull(),
                    () -> assertThat(result.getLoginId()).isEqualTo(new LoginId(loginId)),
                    () -> assertThat(result.getName().masked()).isEqualTo("홍길*"),
                    () -> assertThat(result.getBirthDate().getValue()).isEqualTo(birthDate),
                    () -> assertThat(result.getEmail().getValue()).isEqualTo(email)
            );
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, DUPLICATE_LOGIN_ID 예외가 발생한다.")
        @Test
        void throwsDuplicateLoginIdException_whenLoginIdAlreadyExists() {
            // arrange
            String loginId = "user123";
            userService.register(loginId, "Password1!", "홍길동", "1990-01-01", "test@email.com");

            // act & assert
            assertThatThrownBy(() -> userService.register(loginId, "Password2!", "김철수", "1995-05-05", "other@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.DUPLICATE_LOGIN_ID));
        }
    }

    @DisplayName("로그인을 할 때,")
    @Nested
    class Login {

        @DisplayName("유효한 로그인 정보를 입력하면, 사용자 ID를 반환한다.")
        @Test
        void returnsUserId_whenValidCredentialsProvided() {
            // arrange
            String loginId = "user123";
            String password = "Password1!";
            User user = userService.register(loginId, password, "홍길동", "1990-01-01", "test@email.com");

            // act
            Long userId = userService.login(loginId, password);

            // assert
            assertThat(userId).isEqualTo(user.getId());
        }

        @DisplayName("loginId가 null이면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorizedException_whenLoginIdIsNull() {
            // act & assert
            assertThatThrownBy(() -> userService.login(null, "Password1!"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("loginPw가 null이면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorizedException_whenLoginPwIsNull() {
            // act & assert
            assertThatThrownBy(() -> userService.login("user123", null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("존재하지 않는 loginId를 입력하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorizedException_whenLoginIdDoesNotExist() {
            // act & assert
            assertThatThrownBy(() -> userService.login("nonexistent", "Password1!"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorizedException_whenPasswordDoesNotMatch() {
            // arrange
            String loginId = "user123";
            userService.register(loginId, "Password1!", "홍길동", "1990-01-01", "test@email.com");

            // act & assert
            assertThatThrownBy(() -> userService.login(loginId, "WrongPassword1!"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class UpdatePassword {

        @DisplayName("기존 비밀번호가 일치하면, 비밀번호가 정상적으로 수정된다.")
        @Test
        void updatesPassword_whenOldPasswordMatches() {
            // arrange
            User user = userService.register("user123", "Password1!", "홍길동", "1990-01-01", "test@email.com");
            String oldPassword = "Password1!";
            String newPassword = "NewPassword2@";

            // act
            userService.updatePassword(user.getId(), oldPassword, newPassword);

            // assert
            Long userId = userService.login("user123", newPassword);
            assertThat(userId).isEqualTo(user.getId());
        }

        @DisplayName("존재하지 않는 사용자 ID를 입력하면, USER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsUserNotFoundException_whenUserIdDoesNotExist() {
            // act & assert
            assertThatThrownBy(() -> userService.updatePassword(999L, "Password1!", "NewPassword2@"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND));
        }
    }
}
