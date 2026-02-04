package com.loopers.domain.user;

import com.loopers.application.user.SignUpCommand;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.loopers.infrastructure.user.UserJpaRepository;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
public class UserServiceIntegrationTest {
    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("회원가입하면 DB에 저장된다.")
    void signUp_savesUser() {
        // arrange
        SignUpCommand command = new SignUpCommand(
                "testUser123",
                "ValidPass1!",
                "박자바",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
        );

        // act
        userService.signUp(command);

        // assert
        User savedUser = userJpaRepository.findByLoginId("testUser123")
                                          .orElseThrow(() -> new AssertionError("User not saved"));

        assertThat(savedUser.getLoginId()).isEqualTo("testUser123");
        assertThat(savedUser.getId()).isGreaterThan(0L);
    }
}
