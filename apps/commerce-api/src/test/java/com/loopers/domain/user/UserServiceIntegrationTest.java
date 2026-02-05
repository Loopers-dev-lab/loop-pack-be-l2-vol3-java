package com.loopers.domain.user;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    UserService userService;

    @Autowired
    UserJpaRepository userJpaRepository;

    @Autowired
    DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입 시,")
    @Nested
    class Signup {

        @Test
        void 성공하면_LoginId를_반환하고_DB에_저장된다() {
            // Arrange
            String loginId = "loopers123";
            String password = "loopers123!@";
            String name = "루퍼스";
            LocalDate birthDate = LocalDate.of(1996, 11, 22);
            String email = "test@loopers.im";

            // Act
            LoginId result = userService.signup(loginId, password, name, birthDate, email);

            // Assert
            assertThat(result).isEqualTo(LoginId.from(loginId));

            Optional<User> savedUser = userJpaRepository.findByLoginIdValue(loginId);
            assertThat(savedUser).isPresent();
        }

        @Test
        void 중복된_로그인ID면_CONFLICT를_던진다() {
            // Arrange
            String loginId = "loopers123";
            userService.signup(loginId, "loopers123!@", "루퍼스", LocalDate.of(1996, 11, 22), "test@loopers.im");

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.signup(loginId, "otherPass123!", "다른이름", LocalDate.of(2000, 1, 1), "other@loopers.im");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
