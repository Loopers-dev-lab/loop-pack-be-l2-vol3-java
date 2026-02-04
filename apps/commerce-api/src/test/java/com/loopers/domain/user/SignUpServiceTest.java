package com.loopers.domain.user;

import com.loopers.application.user.SignUpCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

public class SignUpServiceTest {
    private InMemoryUserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private SignUpValidator signUpValidator;
    private SignUpService signUpService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        passwordEncoder = rawPassword -> "encoded_" + rawPassword;
        signUpValidator = new SignUpValidator(userRepository);
        signUpService = new SignUpService(signUpValidator, passwordEncoder, userRepository);
    }

    @Test
    @DisplayName("회원가입시 비밀번호를 암호화해서 저장한다.")
    void signUp_encryptsPassword() {
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
}
