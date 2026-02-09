package com.loopers.domain.user;

import static com.loopers.domain.user.UserFixture.DEFAULT_PASSWORD;
import static com.loopers.domain.user.UserFixture.createPasswordEncoder;
import static com.loopers.domain.user.UserFixture.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class UserTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = createPasswordEncoder();
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class SignUp {

        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllValuesAreValid() {
            assertThatCode(() -> User.signUp("user123", "Password1!", "홍길동", "1990-01-01", "test@email.com", passwordEncoder))
                    .doesNotThrowAnyException();
        }

        @DisplayName("비밀번호는 암호화하여 저장한다.")
        @Test
        void encodesPassword_whenCreatingUser() {
            // arrange
            String rawPassword = "Password1!";

            // act
            User user = User.signUp("user123", rawPassword, "홍길동", "1990-01-01", "test@email.com", passwordEncoder);

            // assert
            assertThat(user.matchesPassword(rawPassword, passwordEncoder)).isTrue();
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, PASSWORD_CONTAINS_BIRTH_DATE 예외가 발생한다.")
        @Test
        void throwsPasswordContainsBirthDateException_whenPasswordContainsBirthDate() {
            // arrange
            String password = "Pass19900101!";
            String birthDate = "1990-01-01";

            // act & assert
            assertThatThrownBy(() -> User.signUp("user123", password, "홍길동", birthDate, "test@email.com", passwordEncoder))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED));
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class UpdatePassword {

        @DisplayName("새 비밀번호가 기존 비밀번호와 다르면, 정상적으로 수정된다.")
        @Test
        void updatesPassword_whenNewPasswordIsDifferent() {
            // arrange
            User user = createUser(passwordEncoder);
            String newPassword = "NewPassword2@";

            // act & assert
            assertThatCode(() -> user.updatePassword(DEFAULT_PASSWORD, newPassword, passwordEncoder))
                    .doesNotThrowAnyException();
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, PASSWORD_MISMATCH 예외가 발생한다.")
        @Test
        void throwsPasswordMismatchException_whenOldPasswordDoesNotMatch() {
            // arrange
            User user = createUser(passwordEncoder);
            String wrongOldPassword = "WrongPassword1!";
            String newPassword = "NewPassword2@";

            // act & assert
            assertThatThrownBy(() -> user.updatePassword(wrongOldPassword, newPassword, passwordEncoder))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH));
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, PASSWORD_REUSE_NOT_ALLOWED 예외가 발생한다.")
        @Test
        void throwsPasswordReuseNotAllowedException_whenNewPasswordIsSameAsOld() {
            // arrange
            User user = createUser(passwordEncoder);

            // act & assert
            assertThatThrownBy(() -> user.updatePassword(DEFAULT_PASSWORD, DEFAULT_PASSWORD, passwordEncoder))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PASSWORD_REUSE_NOT_ALLOWED));
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED 예외가 발생한다.")
        @Test
        void throwsBirthDateInPasswordNotAllowedException_whenNewPasswordContainsBirthDate() {
            // arrange
            User user = createUser(passwordEncoder);
            String newPasswordWithBirthDate = "Pass19900101!";

            // act & assert
            assertThatThrownBy(() -> user.updatePassword(DEFAULT_PASSWORD, newPasswordWithBirthDate, passwordEncoder))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED));
        }
    }
}
