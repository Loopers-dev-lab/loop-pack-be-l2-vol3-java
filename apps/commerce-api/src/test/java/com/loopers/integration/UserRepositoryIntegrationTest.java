package com.loopers.integration;

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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig.class)
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("User 저장 후 ID가 생성된다")
    void save_generates_id() {
        // given
        User user = createUser("test1234");

        // when
        User saved = userRepository.save(user);

        // then
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("User 저장 후 조회 성공")
    void save_and_findById_success() {
        // given
        User user = createUser("test1234");
        userRepository.save(user);

        // when
        var found = userRepository.findById(UserId.of("test1234"));

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getUserId().getValue()).isEqualTo("test1234");
        assertThat(found.get().getUserName().getValue()).isEqualTo("홍길동");
        assertThat(found.get().getEmail().getValue()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("존재하지 않는 User 조회 시 빈 Optional 반환")
    void findById_not_found() {
        // when
        var found = userRepository.findById(UserId.of("notexist"));

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("존재하는 UserId로 existsById 호출 시 true 반환")
    void existsById_returns_true() {
        // given
        User user = createUser("exist123");
        userRepository.save(user);

        // when
        boolean exists = userRepository.existsById(UserId.of("exist123"));

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 UserId로 existsById 호출 시 false 반환")
    void existsById_returns_false() {
        // when
        boolean exists = userRepository.existsById(UserId.of("notexist"));

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("여러 User 저장 후 각각 조회 성공")
    void save_multiple_users() {
        // given
        User user1 = createUser("user0001");
        User user2 = createUserWithEmail("user0002", "user2@example.com");
        userRepository.save(user1);
        userRepository.save(user2);

        // when
        var found1 = userRepository.findById(UserId.of("user0001"));
        var found2 = userRepository.findById(UserId.of("user0002"));

        // then
        assertThat(found1).isPresent();
        assertThat(found2).isPresent();
        assertThat(found1.get().getUserId().getValue()).isEqualTo("user0001");
        assertThat(found2.get().getUserId().getValue()).isEqualTo("user0002");
    }

    private User createUser(String userId) {
        return User.register(
                UserId.of(userId),
                UserName.of("홍길동"),
                "encoded_password",
                Birthday.of(LocalDate.of(1990, 5, 15)),
                Email.of("test@example.com"),
                WrongPasswordCount.init(),
                LocalDateTime.now()
        );
    }

    private User createUserWithEmail(String userId, String email) {
        return User.register(
                UserId.of(userId),
                UserName.of("홍길동"),
                "encoded_password",
                Birthday.of(LocalDate.of(1990, 5, 15)),
                Email.of(email),
                WrongPasswordCount.init(),
                LocalDateTime.now()
        );
    }
}
