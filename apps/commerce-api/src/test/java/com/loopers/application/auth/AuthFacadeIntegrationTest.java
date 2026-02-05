package com.loopers.application.auth;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
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

/**
 * [Application Layer - AuthFacade 통합 테스트]
 *
 * AuthFacade의 비즈니스 흐름을 검증하는 통합 테스트.
 * Spring Context를 로드하고 실제 DB를 사용하여 오케스트레이션 로직을 검증한다.
 *
 * 테스트 범위:
 * - AuthFacade → UserService → UserRepository → DB
 * - 실제 비즈니스 흐름 전체 검증
 */
@SpringBootTest
class AuthFacadeIntegrationTest {

    @Autowired
    private AuthFacade authFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("signup 메서드는")
    class Signup {

        @Test
        @DisplayName("유효한 정보로 가입하면, UserInfo를 반환한다")
        void returnsUserInfoWhenValidInput() {
            // arrange
            String loginId = "nahyeon";
            String password = "Hx7!mK2@";
            String name = "홍길동";
            String birthDate = "1994-11-15";
            String email = "nahyeon@example.com";

            // act
            UserInfo result = authFacade.signup(loginId, password, name, birthDate, email);

            // assert
            assertAll(
                    () -> assertThat(result.loginId()).isEqualTo("nahyeon"),
                    () -> assertThat(result.name()).isEqualTo("홍길동"),
                    () -> assertThat(result.maskedName()).isEqualTo("홍길*"),
                    () -> assertThat(result.birthDate()).isEqualTo(LocalDate.of(1994, 11, 15)),
                    () -> assertThat(result.email()).isEqualTo("nahyeon@example.com")
            );
        }

        @Test
        @DisplayName("가입 후 DB에 사용자가 저장된다")
        void savesUserToDatabase() {
            // arrange
            String loginId = "testuser";
            String password = "Hx7!mK2@";
            String name = "테스트";
            String birthDate = "1990-01-01";
            String email = "test@example.com";

            // act
            authFacade.signup(loginId, password, name, birthDate, email);

            // assert
            assertThat(userRepository.existsByLoginId(loginId)).isTrue();
        }

        @Test
        @DisplayName("중복된 로그인ID로 가입하면, 예외가 발생한다")
        void throwsExceptionWhenDuplicateLoginId() {
            // arrange
            String loginId = "duplicate";
            authFacade.signup(loginId, "Hx7!mK2@", "홍길동", "1994-11-15", "first@example.com");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                authFacade.signup(loginId, "Nw8@pL3#", "김철수", "1995-05-05", "second@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID);
        }

        @Test
        @DisplayName("비밀번호에 생년월일이 포함되면, 예외가 발생한다")
        void throwsExceptionWhenPasswordContainsBirthDate() {
            // arrange
            String loginId = "nahyeon";
            String password = "X19940115!";  // contains birthDate
            String name = "홍길동";
            String birthDate = "1994-01-15";
            String email = "nahyeon@example.com";

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                authFacade.signup(loginId, password, name, birthDate, email);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }

    @Nested
    @DisplayName("changePassword 메서드는")
    class ChangePassword {

        @Test
        @DisplayName("유효한 요청이면, 비밀번호가 변경된다")
        void changesPasswordWhenValidRequest() {
            // arrange
            String loginId = "nahyeon";
            String currentPassword = "Hx7!mK2@";
            String newPassword = "Nw8@pL3#";
            authFacade.signup(loginId, currentPassword, "홍길동", "1994-11-15", "nahyeon@example.com");

            // act
            authFacade.changePassword(loginId, currentPassword, currentPassword, newPassword);

            // assert - 새 비밀번호로 인증 성공 확인
            User user = userRepository.findByLoginId(loginId).orElseThrow();
            assertThat(passwordEncryptor.matches(newPassword, user.getPassword())).isTrue();
        }

        @Test
        @DisplayName("헤더 비밀번호가 틀리면, 인증 예외가 발생한다")
        void throwsExceptionWhenHeaderPasswordWrong() {
            // arrange
            String loginId = "nahyeon";
            String currentPassword = "Hx7!mK2@";
            authFacade.signup(loginId, currentPassword, "홍길동", "1994-11-15", "nahyeon@example.com");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                authFacade.changePassword(loginId, "wrongPw1!", currentPassword, "Nw8@pL3#");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.UNAUTHORIZED);
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면, 예외가 발생한다")
        void throwsExceptionWhenCurrentPasswordWrong() {
            // arrange
            String loginId = "nahyeon";
            String currentPassword = "Hx7!mK2@";
            authFacade.signup(loginId, currentPassword, "홍길동", "1994-11-15", "nahyeon@example.com");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                authFacade.changePassword(loginId, currentPassword, "wrongPw1!", "Nw8@pL3#");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_MISMATCH);
        }

        @Test
        @DisplayName("새 비밀번호가 현재와 동일하면, 예외가 발생한다")
        void throwsExceptionWhenSameAsCurrentPassword() {
            // arrange
            String loginId = "nahyeon";
            String currentPassword = "Hx7!mK2@";
            authFacade.signup(loginId, currentPassword, "홍길동", "1994-11-15", "nahyeon@example.com");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                authFacade.changePassword(loginId, currentPassword, currentPassword, currentPassword);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.SAME_PASSWORD);
        }
    }
}