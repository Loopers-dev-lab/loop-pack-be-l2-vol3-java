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
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입할 때,")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 가입하면, 사용자가 생성된다.")
        @Test
        void createsUser_whenValidInfoIsProvided() {
            // arrange
            String loginId = "testuser";
            String password = "Test1234!";
            String name = "홍길동";
            String birthDate = "19900101";
            String email = "test@example.com";

            // act
            User result = userService.register(loginId, password, name, birthDate, email);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(loginId),
                () -> assertThat(result.getName()).isEqualTo(name),
                () -> assertThat(result.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(result.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenLoginIdAlreadyExists() {
            // arrange
            String loginId = "testuser";
            userService.register(loginId, "Test1234!", "홍길동", "19900101", "test@example.com");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.register(loginId, "Another12!", "김철수", "19950505", "another@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("사용자를 조회할 때,")
    @Nested
    class GetUserByLoginId {

        @DisplayName("존재하는 로그인 ID로 조회하면, 사용자 정보를 반환한다.")
        @Test
        void returnsUser_whenLoginIdExists() {
            // arrange
            String loginId = "testuser";
            userService.register(loginId, "Test1234!", "홍길동", "19900101", "test@example.com");

            // act
            User result = userService.getUserByLoginId(loginId);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(loginId)
            );
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면, USER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsUserNotFoundException_whenLoginIdDoesNotExist() {
            // arrange
            String loginId = "nonexistent";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.getUserByLoginId(loginId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
        }
    }

    @DisplayName("인증할 때,")
    @Nested
    class Authenticate {

        @DisplayName("올바른 로그인 ID와 비밀번호로 인증하면, 사용자 정보를 반환한다.")
        @Test
        void returnsUser_whenCredentialsAreValid() {
            // arrange
            String loginId = "testuser";
            String password = "Test1234!";
            userService.register(loginId, password, "홍길동", "19900101", "test@example.com");

            // act
            User result = userService.authenticate(loginId, password);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(loginId)
            );
        }

        @DisplayName("존재하지 않는 로그인 ID로 인증하면, USER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsUserNotFoundException_whenLoginIdDoesNotExist() {
            // arrange
            String loginId = "nonexistent";
            String password = "Test1234!";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate(loginId, password);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
        }

        @DisplayName("잘못된 비밀번호로 인증하면, PASSWORD_MISMATCH 예외가 발생한다.")
        @Test
        void throwsPasswordMismatchException_whenPasswordIsWrong() {
            // arrange
            String loginId = "testuser";
            userService.register(loginId, "Test1234!", "홍길동", "19900101", "test@example.com");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate(loginId, "WrongPass1!");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 현재 비밀번호와 유효한 새 비밀번호로 변경하면, 성공한다.")
        @Test
        void succeeds_whenCurrentPasswordIsCorrectAndNewPasswordIsValid() {
            // arrange
            String loginId = "testuser";
            String currentPassword = "Test1234!";
            String newPassword = "NewPass12!";
            userService.register(loginId, currentPassword, "홍길동", "19900101", "test@example.com");

            // act
            userService.changePassword(loginId, currentPassword, newPassword);

            // assert
            User updatedUser = userService.authenticate(loginId, newPassword);
            assertThat(updatedUser).isNotNull();
        }

        @DisplayName("잘못된 현재 비밀번호로 변경하면, PASSWORD_MISMATCH 예외가 발생한다.")
        @Test
        void throwsPasswordMismatchException_whenCurrentPasswordIsWrong() {
            // arrange
            String loginId = "testuser";
            userService.register(loginId, "Test1234!", "홍길동", "19900101", "test@example.com");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(loginId, "WrongPass1!", "NewPass12!");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
        }
    }
}
