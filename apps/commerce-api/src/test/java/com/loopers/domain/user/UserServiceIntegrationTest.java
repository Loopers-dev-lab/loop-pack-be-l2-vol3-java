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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    private static final String VALID_LOGIN_ID = "namjin123";
    private static final String VALID_PASSWORD = "qwer@1234";
    private static final String VALID_NAME = "namjin";
    private static final String VALID_BIRTHDAY = "1994-05-25";
    private static final String VALID_EMAIL = "test@gmail.com";

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

    @DisplayName("회원가입을 할 때, ")
    @Nested
    class Signup {

        @DisplayName("정상적인 정보로 회원가입이 성공한다.")
        @Test
        void signupSucceeds_whenInfoIsValid() {
            // act
            UserModel result = userService.signup(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID);

            // DB에 실제로 저장됐는지 확인
            assertThat(userJpaRepository.findById(result.getId())).isPresent();
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdAlreadyExists() {
            // arrange - 먼저 회원 하나 저장
            UserModel existingUser = new UserModel(VALID_LOGIN_ID, "otherPw@123", "기존회원", "1990-01-01", "other@test.com");
            userJpaRepository.save(existingUser);

            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signup(VALID_LOGIN_ID, "newPw@1234", "신규회원", "1995-05-05", "new@test.com");
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("인증을 할 때, ")
    @Nested
    class Authenticate {

        @DisplayName("올바른 자격증명으로 인증이 성공한다.")
        @Test
        void authenticateSucceeds_whenCredentialsAreValid() {
            // arrange - 회원가입 (BCrypt 암호화 포함)
            userService.signup(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act
            UserModel result = userService.authenticate(VALID_LOGIN_ID, VALID_PASSWORD);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID);
        }

        @DisplayName("존재하지 않는 로그인 ID로 인증하면, 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdNotFound() {
            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate("nonexistent", VALID_PASSWORD);
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordDoesNotMatch() {
            // arrange - 회원가입
            userService.signup(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate(VALID_LOGIN_ID, "wrongPw@123");
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        private static final String NEW_PASSWORD = "newPw@1234";

        @DisplayName("정상적인 정보로 비밀번호 변경이 성공한다.")
        @Test
        void changePasswordSucceeds_whenInfoIsValid() {
            // arrange - 회원가입
            userService.signup(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act
            userService.changePassword(VALID_LOGIN_ID, VALID_PASSWORD, NEW_PASSWORD);

            // assert - 새 비밀번호로 인증 성공
            UserModel result = userService.authenticate(VALID_LOGIN_ID, NEW_PASSWORD);
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID);
        }

        @DisplayName("비밀번호 변경 후 기존 비밀번호로 인증하면, 예외가 발생한다.")
        @Test
        void throwsException_whenAuthenticatingWithOldPassword() {
            // arrange - 회원가입 및 비밀번호 변경
            userService.signup(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
            userService.changePassword(VALID_LOGIN_ID, VALID_PASSWORD, NEW_PASSWORD);

            // act & assert - 기존 비밀번호로 인증 실패
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate(VALID_LOGIN_ID, VALID_PASSWORD);
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, 예외가 발생한다.")
        @Test
        void throwsException_whenCurrentPasswordDoesNotMatch() {
            // arrange - 회원가입
            userService.signup(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(VALID_LOGIN_ID, "wrongPw@123", NEW_PASSWORD);
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewPasswordIsSameAsCurrent() {
            // arrange - 회원가입
            userService.signup(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(VALID_LOGIN_ID, VALID_PASSWORD, VALID_PASSWORD);
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("로그인 ID로 조회할 때, ")
    @Nested
    class FindByLoginId {

        @DisplayName("존재하는 로그인 ID로 조회하면, 사용자를 반환한다.")
        @Test
        void returnsUser_whenLoginIdExists() {
            // arrange - 회원가입
            userService.signup(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act
            UserModel result = userService.findByLoginId(VALID_LOGIN_ID);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID);
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면, 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdNotFound() {
            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.findByLoginId("nonexistent");
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
