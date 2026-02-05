package com.loopers.domain.user;

import com.loopers.application.user.SignUpCommand;
import com.loopers.infrastructure.user.BcryptPasswordEncoder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SignUpValidatorTest {
    private InMemoryUserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private SignUpValidator signUpValidator;
    private SignUpService signUpService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        passwordEncoder = new BcryptPasswordEncoder();
        signUpValidator = new SignUpValidator(userRepository);
        signUpService = new SignUpService(signUpValidator, passwordEncoder, userRepository);
    }

    @Test
    @DisplayName("회원가입시 loginId가 중복되면 예외가 발생한다.")
    void validate_throwsException_whenLoginIdIsDuplicated() {
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
            signUpValidator.validate(secondCommand);
        });

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("회원가입시 password가 8자 미만이면 예외가 발생한다.")
    void validate_throwsException_whenPasswordIsTooShort() {
        // arrange
        SignUpCommand command = new SignUpCommand(
                "testUser",
                "Short1!",
                "박자바",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
        );

        // act
        CoreException result = assertThrows(CoreException.class, () -> {
            signUpValidator.validate(command);
        });

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("회원가입시 password가 16자 초과면 예외가 발생한다.")
    void validate_throwsException_whenPasswordIsTooLong() {
        // arrange
        SignUpCommand command = new SignUpCommand(
                "testUser",
                "VeryLongPass123!!",
                "박자바",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
        );

        // act
        CoreException result = assertThrows(CoreException.class, () -> {
            signUpValidator.validate(command);
        });

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("회원가입시 password에 공백이 포함되면 예외가 발생한다.")
    void validate_throwsException_whenPasswordContainsWhitespace() {
        // arrange
        SignUpCommand command = new SignUpCommand(
                "testUser",
                "Pass 1234!",
                "박자바",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
        );

        // act
        CoreException result = assertThrows(CoreException.class, () -> {
            signUpValidator.validate(command);
        });

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("회원가입시 password에 생년월일이 포함되면 예외가 발생한다.")
    void validate_throwsException_whenPasswordContainsBirthDate() {
        // arrange
        SignUpCommand command = new SignUpCommand(
                "testUser",
                "Pass19900115!",
                "박자바",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
        );

        // act
        CoreException result = assertThrows(CoreException.class, () -> {
            signUpValidator.validate(command);
        });

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
