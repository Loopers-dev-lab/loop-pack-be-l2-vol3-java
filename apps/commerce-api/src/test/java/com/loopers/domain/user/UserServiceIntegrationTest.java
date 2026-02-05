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

    @DisplayName("내 정보 조회 시,")
    @Nested
    class GetMyInfo {

        @Test
        void 정상_인증이면_UserInfo를_반환한다() {
            // Arrange
            userService.signup("loopers123", "loopers123!@", "루퍼스", LocalDate.of(1996, 11, 22), "test@loopers.im");

            // Act
            UserInfo result = userService.getMyInfo("loopers123", "loopers123!@");

            // Assert
            assertThat(result.loginId()).isEqualTo("loopers123");
            assertThat(result.maskedName()).isEqualTo("루퍼*");
            assertThat(result.birthDate()).isEqualTo(LocalDate.of(1996, 11, 22));
            assertThat(result.email()).isEqualTo("test@loopers.im");
        }

        @Test
        void 존재하지_않는_loginId면_NOT_FOUND를_던진다() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.getMyInfo("nonexist12", "loopers123!@");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void 비밀번호가_불일치하면_UNAUTHORIZED를_던진다() {
            // Arrange
            userService.signup("loopers123", "loopers123!@", "루퍼스", LocalDate.of(1996, 11, 22), "test@loopers.im");

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.getMyInfo("loopers123", "wrongPass123!");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    class ChangePassword {

        @BeforeEach
        void setUp() {
            userService.signup("loopers123", "loopers123!@", "루퍼스", LocalDate.of(1996, 11, 22), "test@loopers.im");
        }

        @Test
        void 정상_변경이면_새_비밀번호로_인증할_수_있다() {
            // Act
            userService.changePassword("loopers123", "loopers123!@", "newPass1234!");

            // Assert - 새 비밀번호로 조회 성공
            UserInfo result = userService.getMyInfo("loopers123", "newPass1234!");
            assertThat(result.loginId()).isEqualTo("loopers123");
        }

        @Test
        void 존재하지_않는_loginId면_NOT_FOUND를_던진다() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("nonexist12", "loopers123!@", "newPass1234!");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void 기존_비밀번호가_불일치하면_UNAUTHORIZED를_던진다() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("loopers123", "wrongPass123!", "newPass1234!");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @Test
        void 새_비밀번호가_기존과_동일하면_BAD_REQUEST를_던진다() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("loopers123", "loopers123!@", "loopers123!@");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void 새_비밀번호에_생년월일이_포함되면_BAD_REQUEST를_던진다() {
            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword("loopers123", "loopers123!@", "ab19961122!");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
