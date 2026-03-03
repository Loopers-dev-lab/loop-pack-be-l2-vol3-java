package com.loopers.application.user;

import com.loopers.domain.user.InMemoryUserRepository;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.User;
import com.loopers.infrastructure.user.BcryptPasswordEncoder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SignUpServiceTest {
    private InMemoryUserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private SignUpService signUpService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        passwordEncoder = new BcryptPasswordEncoder();
        signUpService = new SignUpService(passwordEncoder, userRepository);
    }

    @DisplayName("회원가입 시, ")
    @Nested
    class SignUp {

        @DisplayName("비밀번호를 암호화해서 저장한다.")
        @Test
        void encryptsPassword() {
            // arrange
            SignUpCommand command = new SignUpCommand(
                    "testUser123",
                    "ValidPass1!",
                    "박자바",
                    LocalDate.of(1990, 1, 15),
                    "test@example.com"
            );

            // act
            signUpService.signUp(command);

            // assert
            User savedUser = userRepository.findByLoginId("testUser123").orElse(null);
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getPassword()).isNotEqualTo("ValidPass1!");
        }

        @DisplayName("loginId가 중복되면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenLoginIdIsDuplicated() {
            // arrange
            SignUpCommand firstCommand = new SignUpCommand(
                    "duplicateId",
                    "ValidPass1!",
                    "박자바",
                    LocalDate.of(1990, 1, 15),
                    "first@example.com"
            );
            signUpService.signUp(firstCommand);

            SignUpCommand secondCommand = new SignUpCommand(
                    "duplicateId",
                    "ValidPass2!",
                    "김자바",
                    LocalDate.of(1995, 5, 20),
                    "second@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                signUpService.signUp(secondCommand);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("password가 8자 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsTooShort() {
            // arrange
            SignUpCommand command = new SignUpCommand(
                    "testUser",
                    "Short1!",
                    "박자바",
                    LocalDate.of(1990, 1, 15),
                    "test@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> signUpService.signUp(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password가 16자 초과이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsTooLong() {
            // arrange
            SignUpCommand command = new SignUpCommand(
                    "testUser",
                    "VeryLongPass123!!",
                    "박자바",
                    LocalDate.of(1990, 1, 15),
                    "test@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> signUpService.signUp(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password에 공백이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsWhitespace() {
            // arrange
            SignUpCommand command = new SignUpCommand(
                    "testUser",
                    "Pass 1234!",
                    "박자바",
                    LocalDate.of(1990, 1, 15),
                    "test@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> signUpService.signUp(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsBirthDate() {
            // arrange
            SignUpCommand command = new SignUpCommand(
                    "testUser",
                    "Pass19900115!",
                    "박자바",
                    LocalDate.of(1990, 1, 15),
                    "test@example.com"
            );

            // act
            CoreException result = assertThrows(CoreException.class, () -> signUpService.signUp(command));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
