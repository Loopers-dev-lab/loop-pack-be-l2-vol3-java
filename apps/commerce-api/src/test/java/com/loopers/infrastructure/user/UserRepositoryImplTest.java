package com.loopers.infrastructure.user;

import com.loopers.domain.user.UserModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({UserRepositoryImpl.class, MySqlTestContainersConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("UserRepository 통합 테스트")
class UserRepositoryImplTest {

    @Autowired
    UserRepositoryImpl userRepository;

    @Test
    @DisplayName("저장 시 ID가 자동 생성된다")
    void save_ShouldPersistWithAutoId() {
        UserModel user = UserModel.createWithEncodedPassword(
                "testuser01", "{bcrypt}pw", "홍길동", "19900101", "a@b.com", "서울"
        );

        UserModel saved = userRepository.save(user);

        assertThat(saved.getUserId()).isNotNull();
        assertThat(saved.getUserId()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("ID로 조회 - 존재하는 사용자")
    void findByUserId_Existing_ShouldReturn() {
        UserModel user = UserModel.createWithEncodedPassword(
                "testuser01", "{bcrypt}pw", "홍길동", "19900101", "a@b.com", "서울"
        );
        UserModel saved = userRepository.save(user);

        Optional<UserModel> found = userRepository.findByUserId(saved.getUserId());

        assertThat(found).isPresent();
        assertThat(found.get().getLoginId()).isEqualTo("testuser01");
    }

    @Test
    @DisplayName("ID로 조회 - 존재하지 않는 사용자")
    void findByUserId_NotExisting_ShouldReturnEmpty() {
        Optional<UserModel> found = userRepository.findByUserId(999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("loginId로 조회 - 존재하는 사용자")
    void findByLoginId_Existing_ShouldReturn() {
        UserModel user = UserModel.createWithEncodedPassword(
                "testuser01", "{bcrypt}pw", "홍길동", "19900101", "a@b.com", "서울"
        );
        userRepository.save(user);

        Optional<UserModel> found = userRepository.findByLoginId("testuser01");

        assertThat(found).isPresent();
        assertThat(found.get().getUserName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("loginId로 조회 - 존재하지 않는 사용자")
    void findByLoginId_NotExisting_ShouldReturnEmpty() {
        Optional<UserModel> found = userRepository.findByLoginId("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByLoginId - 존재하면 true")
    void existsByLoginId_Existing_ShouldReturnTrue() {
        UserModel user = UserModel.createWithEncodedPassword(
                "testuser01", "{bcrypt}pw", "홍길동", "19900101", "a@b.com", "서울"
        );
        userRepository.save(user);

        assertThat(userRepository.existsByLoginId("testuser01")).isTrue();
    }

    @Test
    @DisplayName("existsByLoginId - 존재하지 않으면 false")
    void existsByLoginId_NotExisting_ShouldReturnFalse() {
        assertThat(userRepository.existsByLoginId("nonexistent")).isFalse();
    }
}
