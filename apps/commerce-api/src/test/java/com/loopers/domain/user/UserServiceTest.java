package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * UserService 단위 테스트
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncryptor passwordEncryptor;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        passwordEncryptor = Mockito.mock(PasswordEncryptor.class);
        userService = new UserService(userRepository, passwordEncryptor);
    }

    @DisplayName("회원가입 시,")
    @Nested
    class Signup {

        @Test
        void 유효한_정보면_정상적으로_가입된다() {
            // arrange
            when(userRepository.existsByLoginId(anyString())).thenReturn(false);
            when(passwordEncryptor.encode(anyString())).thenReturn("$2a$10$encodedHash");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            User user = userService.signup("nahyeon", "Hx7!mK2@", "홍길동", "1994-11-15", "nahyeon@example.com");

            // assert
            assertAll(
                    () -> assertThat(user.getLoginId().getValue()).isEqualTo("nahyeon"),
                    () -> assertThat(user.getPassword()).isEqualTo("$2a$10$encodedHash"),
                    () -> assertThat(user.getName().getValue()).isEqualTo("홍길동"),
                    () -> assertThat(user.getBirthDate().getValue()).isEqualTo(java.time.LocalDate.of(1994, 11, 15)),
                    () -> assertThat(user.getEmail().getValue()).isEqualTo("nahyeon@example.com")
            );
        }

        @Test
        void 이미_존재하는_로그인_ID면_예외가_발생한다() {
            // arrange
            when(userRepository.existsByLoginId(anyString())).thenReturn(true);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.signup("nahyeon", "Hx7!mK2@", "홍길동", "1994-11-15", "nahyeon@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID);
        }

        @Test
        void 비밀번호에_생년월일이_포함되면_예외가_발생한다() {
            // arrange
            when(userRepository.existsByLoginId(anyString())).thenReturn(false);

            // act & assert - birthDate: 1990-03-25, password contains "19900325"
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.signup("nahyeon", "X19900325!", "홍길동", "1990-03-25", "nahyeon@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }

    @DisplayName("인증 시,")
    @Nested
    class Authenticate {

        @Test
        void 유효한_ID_PW면_사용자를_반환한다() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$hash",
                    new UserName("홍길동"), new BirthDate("1994-11-15"),
                    new Email("nahyeon@example.com")
            );
            when(userRepository.findByLoginId("nahyeon")).thenReturn(Optional.of(user));
            when(passwordEncryptor.matches("Hx7!mK2@", "$2a$10$hash")).thenReturn(true);

            // act
            User result = userService.authenticate("nahyeon", "Hx7!mK2@");

            // assert
            assertThat(result.getLoginId().getValue()).isEqualTo("nahyeon");
        }

        @Test
        void 존재하지_않는_ID면_예외가_발생한다() {
            // arrange
            when(userRepository.findByLoginId(anyString())).thenReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.authenticate("unknown", "Hx7!mK2@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.UNAUTHORIZED);
        }

        @Test
        void 비밀번호가_불일치하면_예외가_발생한다() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$hash",
                    new UserName("홍길동"), new BirthDate("1994-11-15"),
                    new Email("nahyeon@example.com")
            );
            when(userRepository.findByLoginId("nahyeon")).thenReturn(Optional.of(user));
            when(passwordEncryptor.matches(anyString(), anyString())).thenReturn(false);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.authenticate("nahyeon", "wrongPw1!");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    class ChangePassword {

        @Test
        void 유효한_요청이면_비밀번호가_변경된다() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$oldHash",
                    new UserName("홍길동"), new BirthDate("1994-11-15"),
                    new Email("nahyeon@example.com")
            );
            when(passwordEncryptor.matches("Hx7!mK2@", "$2a$10$oldHash")).thenReturn(true);
            when(passwordEncryptor.matches("Nw8@pL3#", "$2a$10$oldHash")).thenReturn(false);
            when(passwordEncryptor.encode("Nw8@pL3#")).thenReturn("$2a$10$newHash");

            // act
            userService.changePassword(user, "Hx7!mK2@", "Nw8@pL3#");

            // assert
            assertThat(user.getPassword()).isEqualTo("$2a$10$newHash");
        }

        @Test
        void 현재_비밀번호가_틀리면_예외가_발생한다() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$hash",
                    new UserName("홍길동"), new BirthDate("1994-11-15"),
                    new Email("nahyeon@example.com")
            );
            when(passwordEncryptor.matches(anyString(), anyString())).thenReturn(false);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, "wrongPw!", "Nw8@pL3#");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_MISMATCH);
        }

        @Test
        void 새_비밀번호가_현재와_동일하면_예외가_발생한다() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$hash",
                    new UserName("홍길동"), new BirthDate("1994-11-15"),
                    new Email("nahyeon@example.com")
            );
            when(passwordEncryptor.matches("Hx7!mK2@", "$2a$10$hash")).thenReturn(true);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, "Hx7!mK2@", "Hx7!mK2@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.SAME_PASSWORD);
        }

        @Test
        void 새_비밀번호에_생년월일이_포함되면_예외가_발생한다() {
            // arrange - birthDate: 1990-03-25 (연속 동일 문자 없음)
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$hash",
                    new UserName("홍길동"), new BirthDate("1990-03-25"),
                    new Email("nahyeon@example.com")
            );
            when(passwordEncryptor.matches("Hx7!mK2@", "$2a$10$hash")).thenReturn(true);

            // act & assert - newPassword contains "19900325" (birthDate)
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, "Hx7!mK2@", "X19900325!");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }
}
