package com.loopers.application.auth;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
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
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuthFacadeIntegrationTest {

    @Autowired
    private AuthFacade authFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("createUser 메서드는")
    class Signup {

        @Test
        void 유효한_정보로_가입하면_UserInfo를_반환한다() {
            // arrange
            String loginId = "nahyeon";
            String password = "Hx7!mK2@";
            String name = "홍길동";
            String birthDate = "1994-11-15";
            String email = "nahyeon@example.com";

            // act
            UserInfo result = authFacade.createUser(loginId, password, name, birthDate, email, Gender.MALE);

            // assert
            assertAll(
                    () -> assertThat(result.loginId()).isEqualTo("nahyeon"),
                    () -> assertThat(result.name()).isEqualTo("홍길동"),
                    () -> assertThat(result.maskedName()).isEqualTo("홍길*"),
                    () -> assertThat(result.birthDate()).isEqualTo(LocalDate.of(1994, 11, 15)),
                    () -> assertThat(result.email()).isEqualTo("nahyeon@example.com"),
                    () -> assertThat(result.gender()).isEqualTo(Gender.MALE)
            );
        }

        @Test
        void 가입_후_DB에_사용자가_저장된다() {
            // arrange
            String loginId = "testuser";
            String password = "Hx7!mK2@";
            String name = "테스트";
            String birthDate = "1990-01-01";
            String email = "test@example.com";

            // act
            authFacade.createUser(loginId, password, name, birthDate, email, Gender.MALE);

            // assert
            assertThat(userRepository.existsByLoginId(loginId)).isTrue();
        }

        @Test
        void 중복된_로그인ID로_가입하면_예외가_발생한다() {
            // arrange
            String loginId = "duplicate";
            authFacade.createUser(loginId, "Hx7!mK2@", "홍길동", "1994-11-15", "first@example.com", Gender.MALE);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                authFacade.createUser(loginId, "Nw8@pL3#", "김철수", "1995-05-05", "second@example.com", Gender.FEMALE);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID);
        }

        @Test
        void 비밀번호에_생년월일이_포함되면_예외가_발생한다() {
            // arrange
            String loginId = "nahyeon";
            String password = "X19940115!";  // contains birthDate
            String name = "홍길동";
            String birthDate = "1994-01-15";
            String email = "nahyeon@example.com";

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                authFacade.createUser(loginId, password, name, birthDate, email, Gender.MALE);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }

    @Nested
    @DisplayName("updateUserPassword 메서드는")
    class ChangePassword {

        @Test
        void 유효한_요청이면_비밀번호가_변경된다() {
            // arrange
            String loginId = "nahyeon";
            String currentPassword = "Hx7!mK2@";
            String newPassword = "Nw8@pL3#";
            authFacade.createUser(loginId, currentPassword, "홍길동", "1994-11-15", "nahyeon@example.com", Gender.MALE);

            // act
            authFacade.updateUserPassword(loginId, currentPassword, currentPassword, newPassword);

            // assert - 새 비밀번호로 인증 성공 확인
            User user = userRepository.findByLoginId(loginId).orElseThrow();
            assertThat(passwordEncryptor.matches(newPassword, user.getPassword())).isTrue();
        }

        @Test
        void 변경된_비밀번호로_재인증에_성공하고_이전_비밀번호는_실패한다() {
            // arrange
            String loginId = "nahyeon";
            String oldPassword = "Hx7!mK2@";
            String newPassword = "Nw8@pL3#";
            authFacade.createUser(loginId, oldPassword, "홍길동", "1994-11-15", "nahyeon@example.com", Gender.MALE);

            // act - 비밀번호 변경 (authenticateUser → detached entity → updateUserPassword에서 save)
            authFacade.updateUserPassword(loginId, oldPassword, oldPassword, newPassword);

            // assert - DB에서 재조회하여 새 비밀번호로 인증 성공 확인
            User reloadedUser = userRepository.findByLoginId(loginId).orElseThrow();
            assertAll(
                    () -> assertThat(passwordEncryptor.matches(newPassword, reloadedUser.getPassword())).isTrue(),
                    () -> assertThat(passwordEncryptor.matches(oldPassword, reloadedUser.getPassword())).isFalse()
            );
        }

        @Test
        void 헤더_비밀번호가_틀리면_인증_예외가_발생한다() {
            // arrange
            String loginId = "nahyeon";
            String currentPassword = "Hx7!mK2@";
            authFacade.createUser(loginId, currentPassword, "홍길동", "1994-11-15", "nahyeon@example.com", Gender.MALE);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                authFacade.updateUserPassword(loginId, "wrongPw1!", currentPassword, "Nw8@pL3#");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.UNAUTHORIZED);
        }

        @Test
        void 현재_비밀번호가_틀리면_예외가_발생한다() {
            // arrange
            String loginId = "nahyeon";
            String currentPassword = "Hx7!mK2@";
            authFacade.createUser(loginId, currentPassword, "홍길동", "1994-11-15", "nahyeon@example.com", Gender.MALE);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                authFacade.updateUserPassword(loginId, currentPassword, "wrongPw1!", "Nw8@pL3#");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_MISMATCH);
        }

        @Test
        void 새_비밀번호가_현재와_동일하면_예외가_발생한다() {
            // arrange
            String loginId = "nahyeon";
            String currentPassword = "Hx7!mK2@";
            authFacade.createUser(loginId, currentPassword, "홍길동", "1994-11-15", "nahyeon@example.com", Gender.MALE);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                authFacade.updateUserPassword(loginId, currentPassword, currentPassword, currentPassword);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.SAME_PASSWORD);
        }
    }
}
