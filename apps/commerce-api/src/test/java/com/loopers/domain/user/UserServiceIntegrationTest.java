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
    private DatabaseCleanUp databaseCleanUp;

    private static final String VALID_LOGIN_ID = "testuser123";
    private static final String VALID_PASSWORD = "Password1!";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 5, 15);
    private static final String VALID_EMAIL = "test@example.com";

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입 시,")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 가입하면, 사용자가 생성된다.")
        @Test
        void createsUser_whenValidInfoIsProvided() {
            // arrange & act
            User result = userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // assert
            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(result.getName()).isEqualTo(VALID_NAME)
            );
        }

        @DisplayName("비밀번호가 BCrypt로 암호화되어 저장된다.")
        @Test
        void encryptsPassword_whenUserIsRegistered() {
            // arrange & act
            User result = userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // assert
            assertAll(
                () -> assertThat(result.getPassword()).isNotEqualTo(VALID_PASSWORD),
                () -> assertThat(result.getPassword()).startsWith("$2a$")
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.register(VALID_LOGIN_ID, VALID_PASSWORD, "다른이름", VALID_BIRTH_DATE, "other@example.com")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("회원 조회 시,")
    @Nested
    class GetUser {

        @DisplayName("존재하는 ID로 조회하면, 사용자 정보를 반환한다.")
        @Test
        void returnsUser_whenUserExists() {
            // arrange
            User savedUser = userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            User result = userService.getUser(savedUser.getId());

            // assert
            assertAll(
                () -> assertThat(result.getId()).isEqualTo(savedUser.getId()),
                () -> assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID)
            );
        }

        @DisplayName("존재하지 않는 ID로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            // arrange
            Long nonExistentId = 999L;

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.getUser(nonExistentId)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("인증 시,")
    @Nested
    class Authenticate {

        @DisplayName("로그인 ID와 비밀번호가 일치하면, 사용자 정보를 반환한다.")
        @Test
        void returnsUser_whenCredentialsMatch() {
            // arrange
            userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            User result = userService.authenticate(VALID_LOGIN_ID, VALID_PASSWORD);

            // assert
            assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID);
        }

        @DisplayName("비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordDoesNotMatch() {
            // arrange
            userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.authenticate(VALID_LOGIN_ID, "WrongPassword1!")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    class ChangePassword {

        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 유효하면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
            // arrange
            User savedUser = userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            String newPassword = "NewPass123!";

            // act
            userService.changePassword(savedUser.getId(), VALID_PASSWORD, newPassword);

            // assert
            User updatedUser = userService.getUser(savedUser.getId());
            assertThat(updatedUser.getPassword()).startsWith("$2a$");
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordDoesNotMatch() {
            // arrange
            User savedUser = userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword(savedUser.getId(), "WrongPassword1!", "NewPass123!")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            User savedUser = userService.register(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                userService.changePassword(savedUser.getId(), VALID_PASSWORD, VALID_PASSWORD)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
