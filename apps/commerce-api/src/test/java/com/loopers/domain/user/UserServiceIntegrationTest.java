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
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입 시")
    @Nested
    class Register {

        @DisplayName("정상 요청이면 User가 DB에 저장되고, 비밀번호는 BCrypt 해시로 저장된다")
        @Test
        void savesUserWithHashedPassword_whenValidRequest() {
            // arrange
            String loginId = "testuser2";
            String rawPassword = "Test1234!";
            String name = "홍길동";
            String email = "test@example.com";
            String birthDate = "1990-01-15";

            // act
            User savedUser = userService.register(loginId, rawPassword, name, email, birthDate);

            // assert
            User foundUser = userJpaRepository.findById(savedUser.getId()).orElseThrow();
            assertAll(
                () -> assertThat(foundUser.getLoginId()).isEqualTo(loginId),
                () -> assertThat(foundUser.getName()).isEqualTo(name),
                () -> assertThat(foundUser.getEmail()).isEqualTo(email),
                () -> assertThat(foundUser.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(foundUser.getPassword()).isNotEqualTo(rawPassword),
                () -> assertThat(passwordEncoder.matches(rawPassword, foundUser.getPassword())).isTrue()
            );
        }

        @DisplayName("이미 존재하는 loginId로 가입 시도 시 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflictException_whenLoginIdAlreadyExists() {
            // arrange
            String loginId = "testuser1";
            String rawPassword = "Test1234!";
            String name = "홍길동";
            String email = "test@example.com";
            String birthDate = "1990-01-15";

            userService.register(loginId, rawPassword, name, email, birthDate);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.register(loginId, "Other1234!", "김철수", "other@example.com", "1985-05-20");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
