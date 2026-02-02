package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class SignUp {

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
            User user = userService.signUp(loginId, password, name, birthDate, email);

            // assert
            assertAll(
                    () -> assertThat(user.getId()).isNotNull(),
                    () -> assertThat(user.getLoginId()).isEqualTo(new LoginId(loginId)),
                    () -> assertThat(user.getName()).isEqualTo(new UserName(name)),
                    () -> assertThat(user.getBirthDate()).isEqualTo(new BirthDate(birthDate)),
                    () -> assertThat(user.getEmail()).isEqualTo(new Email(email)),
                    () -> assertThat(user.getPassword().getValue()).isNotEqualTo(password)
            );
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdAlreadyExists() {
            // arrange
            String loginId = "user123";
            userService.signUp(loginId, "Password1!", "홍길동", "1990-01-01", "test@email.com");

            // act & assert
            assertThatThrownBy(() -> userService.signUp(loginId, "Password2!", "김철수", "1995-05-05", "other@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("사용자 정보를 조회할 때,")
    @Nested
    class GetUser {

        @DisplayName("존재하는 사용자 ID를 입력하면, 해당 사용자의 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenUserIdExists() {
            // arrange
            User savedUser = userService.signUp("user123", "Password1!", "홍길동", "1990-01-01", "test@email.com");

            // act
            User user = userService.getUser(savedUser.getId());

            // assert
            assertThat(user.getId()).isEqualTo(savedUser.getId());
        }

        @DisplayName("존재하지 않는 사용자 ID를 입력하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenUserIdDoesNotExist() {
            // arrange
            Long nonExistentUserId = 999L;

            // act & assert
            assertThatThrownBy(() -> userService.getUser(nonExistentUserId))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class UpdatePassword {

        @DisplayName("기존 비밀번호가 일치하면, 비밀번호가 정상적으로 수정된다.")
        @Test
        void updatesPassword_whenOldPasswordMatches() {
            // arrange
            User user = userService.signUp("user123", "Password1!", "홍길동", "1990-01-01", "test@email.com");
            String oldPassword = "Password1!";
            String newPassword = "NewPassword2@";

            // act
            userService.updatePassword(user.getId(), oldPassword, newPassword);

            // assert
            User updatedUser = userService.getUser(user.getId());
            assertThat(updatedUser.matchesPassword(newPassword, passwordEncoder)).isTrue();
        }

        @DisplayName("존재하지 않는 사용자 ID를 입력하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenUserIdDoesNotExist() {
            // arrange
            String oldPassword = "Password1!";
            String newPassword = "NewPassword2@";
            Long nonExistentUserId = 999L;

            // act & assert
            assertThatThrownBy(() -> userService.updatePassword(nonExistentUserId, oldPassword, newPassword))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
