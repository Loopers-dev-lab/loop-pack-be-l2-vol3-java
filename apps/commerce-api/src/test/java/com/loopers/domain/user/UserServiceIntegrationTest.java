package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UserService 통합 테스트
 *
 * 실제 DB(Testcontainers)를 사용하여 UserService의 비즈니스 흐름을 검증한다.
 *
 * 테스트 범위:
 * - UserService → UserRepository → DB
 * - 실제 비밀번호 암호화 + DB 영속화
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

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
    @DisplayName("회원가입 시,")
    class Signup {

        @Test
        void 유효한_정보면_DB에_저장된다() {
            // act
            User user = userService.createUser("nahyeon", "Hx7!mK2@", "홍길동", "1994-11-15", "nahyeon@example.com");

            // assert
            assertAll(
                    () -> assertThat(user.getId()).isNotNull(),
                    () -> assertThat(userRepository.existsByLoginId("nahyeon")).isTrue()
            );
        }

        @Test
        void 비밀번호가_BCrypt로_암호화되어_저장된다() {
            // act
            userService.createUser("nahyeon", "Hx7!mK2@", "홍길동", "1994-11-15", "nahyeon@example.com");

            // assert
            User saved = userRepository.findByLoginId("nahyeon").orElseThrow();
            assertAll(
                    () -> assertThat(saved.getPassword()).startsWith("$2a$"),
                    () -> assertThat(passwordEncryptor.matches("Hx7!mK2@", saved.getPassword())).isTrue()
            );
        }

        @Test
        void 중복된_로그인ID면_예외가_발생한다() {
            // arrange
            userService.createUser("duplicate", "Hx7!mK2@", "홍길동", "1994-11-15", "first@example.com");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.createUser("duplicate", "Nw8@pL3#", "김철수", "1995-05-05", "second@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID);
        }

        @Test
        void 비밀번호에_생년월일이_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.createUser("nahyeon", "X19940115!", "홍길동", "1994-01-15", "nahyeon@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 시,")
    class ChangePassword {

        @Test
        void 유효한_요청이면_DB에_새_비밀번호가_반영된다() {
            // arrange
            userService.createUser("nahyeon", "Hx7!mK2@", "홍길동", "1994-11-15", "nahyeon@example.com");
            User user = userService.authenticateUser("nahyeon", "Hx7!mK2@");

            // act
            userService.updateUserPassword(user, "Hx7!mK2@", "Nw8@pL3#");

            // assert
            User reloaded = userRepository.findByLoginId("nahyeon").orElseThrow();
            assertAll(
                    () -> assertThat(passwordEncryptor.matches("Nw8@pL3#", reloaded.getPassword())).isTrue(),
                    () -> assertThat(passwordEncryptor.matches("Hx7!mK2@", reloaded.getPassword())).isFalse()
            );
        }

        @Test
        void 현재_비밀번호가_틀리면_예외가_발생한다() {
            // arrange
            userService.createUser("nahyeon", "Hx7!mK2@", "홍길동", "1994-11-15", "nahyeon@example.com");
            User user = userService.authenticateUser("nahyeon", "Hx7!mK2@");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.updateUserPassword(user, "wrongPw1!", "Nw8@pL3#");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_MISMATCH);
        }

        @Test
        void 새_비밀번호가_현재와_동일하면_예외가_발생한다() {
            // arrange
            userService.createUser("nahyeon", "Hx7!mK2@", "홍길동", "1994-11-15", "nahyeon@example.com");
            User user = userService.authenticateUser("nahyeon", "Hx7!mK2@");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.updateUserPassword(user, "Hx7!mK2@", "Hx7!mK2@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.SAME_PASSWORD);
        }
    }
}
