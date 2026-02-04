package com.loopers.domain.user;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static final String LOGIN_ID = "testUser1";
    private static final String RAW_PASSWORD = "Abcd1234!";
    private static final String NAME = "홍길동";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 15);
    private static final String EMAIL = "test@example.com";

    @DisplayName("유저 인증을 할 때, ")
    @Nested
    class Authenticate {

        @DisplayName("올바른 로그인 ID와 비밀번호가 주어지면, 유저를 반환한다.")
        @Test
        void returnsUser_whenCredentialsAreValid() {
            // arrange
            userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // act
            User result = userService.authenticate(LOGIN_ID, RAW_PASSWORD);

            // assert
            assertAll(
                () -> assertThat(result.getLoginId()).isEqualTo(LOGIN_ID),
                () -> assertThat(result.getName()).isEqualTo(NAME),
                () -> assertThat(result.getBirthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(result.getEmail()).isEqualTo(EMAIL)
            );
        }

        @DisplayName("존재하지 않는 로그인 ID가 주어지면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginIdNotFound() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate("nonExistentId", RAW_PASSWORD);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            // arrange
            userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate(LOGIN_ID, "WrongPass1!");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 기존 비밀번호와 새 비밀번호가 주어지면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenCurrentPasswordIsCorrectAndNewPasswordIsValid() {
            // arrange
            User user = userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);
            String newPassword = "NewPass123!";

            // act
            userService.changePassword(user, RAW_PASSWORD, newPassword);

            // assert
            User updated = userService.authenticate(LOGIN_ID, newPassword);
            assertThat(updated.getLoginId()).isEqualTo(LOGIN_ID);
        }

        @DisplayName("기존 비밀번호가 틀리면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordIsWrong() {
            // arrange
            User user = userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, "WrongPass1!", "NewPass123!");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            User user = userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, RAW_PASSWORD, RAW_PASSWORD);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 비밀번호 규칙에 위반되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordViolatesPolicy() {
            // arrange
            User user = userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, RAW_PASSWORD, "short");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("회원가입을 할 때, ")
    @Nested
    class Signup {

        @DisplayName("올바른 정보가 주어지면, 유저가 저장되고 반환된다.")
        @Test
        void savesAndReturnsUser_whenValidInfoProvided() {
            // act
            User result = userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // assert
            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(LOGIN_ID),
                () -> assertThat(result.getName()).isEqualTo(NAME),
                () -> assertThat(result.getBirthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(result.getEmail()).isEqualTo(EMAIL)
            );
        }

        @DisplayName("저장된 비밀번호가 암호화되어 있다.")
        @Test
        void storesEncryptedPassword() {
            // act
            User result = userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // assert
            assertAll(
                () -> assertThat(result.getPassword()).isNotEqualTo(RAW_PASSWORD),
                () -> assertThat(passwordEncryptor.matches(RAW_PASSWORD, result.getPassword())).isTrue()
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signup(LOGIN_ID, RAW_PASSWORD, "김철수", LocalDate.of(2000, 1, 1), "other@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
