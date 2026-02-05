package com.loopers.integration;

import com.loopers.application.service.UserRegisterService;
import com.loopers.domain.model.*;
import com.loopers.domain.repository.UserRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig.class)
class UserRegisterServiceIntegrationTest {

    @Autowired
    private UserRegisterService userRegisterService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("회원가입 후 DB에서 조회 성공")
    void register_and_find_success() {
        // given
        UserId userId = UserId.of("newuser1");
        UserName userName = UserName.of("홍길동");
        Birthday birthday = Birthday.of(LocalDate.of(1990, 5, 15));
        Email email = Email.of("test@example.com");
        String encodedPassword = "encoded_password";

        // when
        userRegisterService.register(userId, userName, encodedPassword, birthday, email);

        // then
        var found = userRepository.findById(userId);
        assertThat(found).isPresent();
        assertThat(found.get().getUserId().getValue()).isEqualTo("newuser1");
        assertThat(found.get().getUserName().getValue()).isEqualTo("홍길동");
        assertThat(found.get().getEmail().getValue()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("중복 ID로 가입 시 예외 발생 - 실제 DB 검증")
    void register_fail_duplicate_id() {
        // given
        UserId userId = UserId.of("dupuser1");
        UserName userName = UserName.of("홍길동");
        Birthday birthday = Birthday.of(LocalDate.of(1990, 5, 15));
        Email email = Email.of("test@example.com");
        String encodedPassword = "encoded_password";

        // 첫 번째 가입 성공
        userRegisterService.register(userId, userName, encodedPassword, birthday, email);

        // when & then - 두 번째 가입 시도 시 예외
        assertThatThrownBy(() ->
                userRegisterService.register(userId, userName, encodedPassword, birthday, email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용중인 ID");
    }

    @Test
    @DisplayName("여러 사용자 가입 성공")
    void register_multiple_users_success() {
        // given
        UserId userId1 = UserId.of("multi001");
        UserId userId2 = UserId.of("multi002");
        UserName userName = UserName.of("홍길동");
        Birthday birthday = Birthday.of(LocalDate.of(1990, 5, 15));
        String encodedPassword = "encoded_password";

        // when
        userRegisterService.register(userId1, userName, encodedPassword, birthday, Email.of("user1@example.com"));
        userRegisterService.register(userId2, userName, encodedPassword, birthday, Email.of("user2@example.com"));

        // then
        assertThat(userRepository.existsById(userId1)).isTrue();
        assertThat(userRepository.existsById(userId2)).isTrue();
    }
}
