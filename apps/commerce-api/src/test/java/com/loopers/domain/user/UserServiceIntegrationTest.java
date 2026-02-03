package com.loopers.domain.user;

import com.loopers.interfaces.api.user.dto.CreateUserRequestV1;
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
        User savedUser = userJpaRepository.findByLoginId("testUser123")
                                          .orElseThrow(() -> new AssertionError("User not saved"));

        assertThat(savedUser.getLoginId()).isEqualTo("testUser123");
        assertThat(savedUser.getId()).isGreaterThan(0L);
    }
}
