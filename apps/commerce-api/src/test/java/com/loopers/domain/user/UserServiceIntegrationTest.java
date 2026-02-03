package com.loopers.domain.user;

import com.loopers.interfaces.api.user.dto.CreateUserRequestV1;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserServiceIntegrationTest {
    private InMemoryUserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        passwordEncoder = rawPassword -> "encoded_" + rawPassword;
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("회원가입시 비밀번호를 암호화해서 저장한다.")
    void signUp_encryptsPassword() {
        // arrange
        CreateUserRequestV1 request = CreateUserRequestV1.builder()
                                                         .loginId("testUser123")
                                                         .password("ValidPass1!")
                                                         .name("박자바")
                                                         .birthDate(LocalDate.of(1990, 1, 15))
                                                         .email("test@example.com")
                                                         .build();

        // act
        userService.signUp(request);

        // assert
        User savedUser = userRepository.findByLoginId("testUser123").orElse(null);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getPassword()).isNotEqualTo("ValidPass1!");
    }

    @Test
    @DisplayName("회원가입시 loginId가 중복되면 예외가 발생한다.")
    void signUp_throwsException_whenLoginIdIsDuplicated() {
        // arrange
        CreateUserRequestV1 firstRequest = CreateUserRequestV1.builder()
                                                              .loginId("duplicateId")
                                                              .password("ValidPass1!")
                                                              .name("박자바")
                                                              .birthDate(LocalDate.of(1990, 1, 15))
                                                              .email("first@example.com")
                                                              .build();
        userService.signUp(firstRequest);

        CreateUserRequestV1 secondRequest = CreateUserRequestV1.builder()
                                                               .loginId("duplicateId")
                                                               .password("ValidPass2!")
                                                               .name("김자바")
                                                               .birthDate(LocalDate.of(1995, 5, 20))
                                                               .email("second@example.com")
                                                               .build();

        // act
        CoreException result = assertThrows(CoreException.class, () -> {
            userService.signUp(secondRequest);
        });

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    @DisplayName("회원가입시 password가 8자 미만이면 예외가 발생한다.")
    void signUp_throwsException_whenPasswordIsTooShort() {
        // arrange
        CreateUserRequestV1 request = CreateUserRequestV1.builder()
                                                         .loginId("testUser")
                                                         .password("Short1!")
                                                         .name("박자바")
                                                         .birthDate(LocalDate.of(1990, 1, 15))
                                                         .email("test@example.com")
                                                         .build();

        // act
        CoreException result = assertThrows(CoreException.class, () -> {
            userService.signUp(request);
        });

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("회원가입시 password가 16자 초과면 예외가 발생한다.")
    void signUp_throwsException_whenPasswordIsTooLong() {
        // arrange
        CreateUserRequestV1 request = CreateUserRequestV1.builder()
                                                         .loginId("testUser")
                                                         .password("VeryLongPass123!!")
                                                         .name("박자바")
                                                         .birthDate(LocalDate.of(1990, 1, 15))
                                                         .email("test@example.com")
                                                         .build();

        // act
        CoreException result = assertThrows(CoreException.class, () -> {
            userService.signUp(request);
        });

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("회원가입시 password에 공백이 포함되면 예외가 발생한다.")
    void signUp_throwsException_whenPasswordContainsWhitespace() {
        // arrange
        CreateUserRequestV1 request = CreateUserRequestV1.builder()
                                                         .loginId("testUser")
                                                         .password("Pass 1234!")
                                                         .name("박자바")
                                                         .birthDate(LocalDate.of(1990, 1, 15))
                                                         .email("test@example.com")
                                                         .build();

        // act
        CoreException result = assertThrows(CoreException.class, () -> {
            userService.signUp(request);
        });

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("회원가입시 password에 생년월일이 포함되면 예외가 발생한다.")
    void signUp_throwsException_whenPasswordContainsBirthDate() {
        // arrange
        CreateUserRequestV1 request = CreateUserRequestV1.builder()
                                                         .loginId("testUser")
                                                         .password("Pass19900115!")
                                                         .name("박자바")
                                                         .birthDate(LocalDate.of(1990, 1, 15))
                                                         .email("test@example.com")
                                                         .build();

        // act
        CoreException result = assertThrows(CoreException.class, () -> {
            userService.signUp(request);
        });

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
