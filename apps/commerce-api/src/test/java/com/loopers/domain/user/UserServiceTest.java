package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
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

        @DisplayName("유효한 정보면, 정상적으로 가입된다.")
        @Test
        void signup_whenValidInput() {
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
                    () -> assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(1994, 11, 15)),
                    () -> assertThat(user.getEmail().getValue()).isEqualTo("nahyeon@example.com")
            );
        }

        @DisplayName("이미 존재하는 로그인 ID면, 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateLoginId() {
            // arrange
            when(userRepository.existsByLoginId(anyString())).thenReturn(true);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.signup("nahyeon", "Hx7!mK2@", "홍길동", "1994-11-15", "nahyeon@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordContainsBirthDate() {
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

        @DisplayName("유효한 ID/PW면, 사용자를 반환한다.")
        @Test
        void returnsUser_whenValidCredentials() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$hash",
                    new UserName("홍길동"), LocalDate.of(1994, 11, 15),
                    new Email("nahyeon@example.com")
            );
            when(userRepository.findByLoginId("nahyeon")).thenReturn(Optional.of(user));
            when(passwordEncryptor.matches("Hx7!mK2@", "$2a$10$hash")).thenReturn(true);

            // act
            User result = userService.authenticate("nahyeon", "Hx7!mK2@");

            // assert
            assertThat(result.getLoginId().getValue()).isEqualTo("nahyeon");
        }

        @DisplayName("존재하지 않는 ID면, 예외가 발생한다.")
        @Test
        void throwsException_whenUserNotFound() {
            // arrange
            when(userRepository.findByLoginId(anyString())).thenReturn(Optional.empty());

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.authenticate("unknown", "Hx7!mK2@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 불일치하면, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordMismatch() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$hash",
                    new UserName("홍길동"), LocalDate.of(1994, 11, 15),
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

        @DisplayName("유효한 요청이면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenValidRequest() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$oldHash",
                    new UserName("홍길동"), LocalDate.of(1994, 11, 15),
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

        @DisplayName("현재 비밀번호가 틀리면, 예외가 발생한다.")
        @Test
        void throwsException_whenCurrentPasswordWrong() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$hash",
                    new UserName("홍길동"), LocalDate.of(1994, 11, 15),
                    new Email("nahyeon@example.com")
            );
            when(passwordEncryptor.matches(anyString(), anyString())).thenReturn(false);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, "wrongPw!", "Nw8@pL3#");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_MISMATCH);
        }

        @DisplayName("새 비밀번호가 현재와 동일하면, 예외가 발생한다.")
        @Test
        void throwsException_whenSameAsCurrentPassword() {
            // arrange
            User user = User.create(
                    new LoginId("nahyeon"), "$2a$10$hash",
                    new UserName("홍길동"), LocalDate.of(1994, 11, 15),
                    new Email("nahyeon@example.com")
            );
            when(passwordEncryptor.matches("Hx7!mK2@", "$2a$10$hash")).thenReturn(true); // current matches
            when(passwordEncryptor.matches("Hx7!mK2@", "$2a$10$hash")).thenReturn(true); // same check also matches

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.changePassword(user, "Hx7!mK2@", "Hx7!mK2@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.SAME_PASSWORD);
        }
    }
}
