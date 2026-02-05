package com.loopers.domain.user;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * [Infrastructure Layer - Repository 통합 테스트]
 *
 * UserRepository 인터페이스의 DB 연동을 검증하는 통합 테스트.
 * Spring Context를 로드하고 Testcontainers로 실제 MySQL을 사용한다.
 *
 * 테스트 범위:
 * - UserRepository (interface) → UserRepositoryImpl → UserJpaRepository → DB
 * - 실제 쿼리 실행 및 데이터 저장/조회 검증
 *
 * 테스트 격리:
 * - @AfterEach에서 truncateAllTables() 호출
 * - 각 테스트가 독립적으로 실행될 수 있도록 보장
 */
@SpringBootTest
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /**
     * 테스트용 User 엔티티 생성 헬퍼 메서드
     */
    private User createTestUser(String loginIdValue) {
        return User.create(
                new LoginId(loginIdValue),
                "$2a$10$encodedPasswordHash",
                new UserName("홍길동"),
                LocalDate.of(1994, 11, 15),
                new Email(loginIdValue + "@example.com")
        );
    }

    @Nested
    @DisplayName("save 메서드는")
    class Save {

        @Test
        @DisplayName("새로운 사용자를 저장하면, ID가 생성된다")
        void createsIdWhenSavingNewUser() {
            // arrange
            User user = createTestUser("testuser");

            // act
            User savedUser = userRepository.save(user);

            // assert
            assertThat(savedUser.getId()).isNotNull();
        }

        @Test
        @DisplayName("저장된 사용자의 모든 필드가 올바르게 저장된다")
        void savesAllFieldsCorrectly() {
            // arrange
            LoginId loginId = new LoginId("nahyeon");
            String encodedPassword = "$2a$10$encodedPasswordHash";
            UserName name = new UserName("홍길동");
            LocalDate birthDate = LocalDate.of(1994, 11, 15);
            Email email = new Email("nahyeon@example.com");

            User user = User.create(loginId, encodedPassword, name, birthDate, email);

            // act
            User savedUser = userRepository.save(user);

            // assert
            assertAll(
                    () -> assertThat(savedUser.getLoginId().getValue()).isEqualTo("nahyeon"),
                    () -> assertThat(savedUser.getPassword()).isEqualTo(encodedPassword),
                    () -> assertThat(savedUser.getName().getValue()).isEqualTo("홍길동"),
                    () -> assertThat(savedUser.getBirthDate()).isEqualTo(birthDate),
                    () -> assertThat(savedUser.getEmail().getValue()).isEqualTo("nahyeon@example.com")
            );
        }
    }

    @Nested
    @DisplayName("findByLoginId 메서드는")
    class FindByLoginId {

        @Test
        @DisplayName("존재하는 로그인ID로 조회하면, 해당 사용자를 반환한다")
        void returnsUserWhenLoginIdExists() {
            // arrange
            User user = createTestUser("existinguser");
            userRepository.save(user);

            // act
            Optional<User> result = userRepository.findByLoginId("existinguser");

            // assert
            assertAll(
                    () -> assertThat(result).isPresent(),
                    () -> assertThat(result.get().getLoginId().getValue()).isEqualTo("existinguser")
            );
        }

        @Test
        @DisplayName("존재하지 않는 로그인ID로 조회하면, empty를 반환한다")
        void returnsEmptyWhenLoginIdNotExists() {
            // arrange
            String nonExistingLoginId = "nonexisting";

            // act
            Optional<User> result = userRepository.findByLoginId(nonExistingLoginId);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByLoginId 메서드는")
    class ExistsByLoginId {

        @Test
        @DisplayName("존재하는 로그인ID면, true를 반환한다")
        void returnsTrueWhenLoginIdExists() {
            // arrange
            User user = createTestUser("existinguser");
            userRepository.save(user);

            // act
            boolean exists = userRepository.existsByLoginId("existinguser");

            // assert
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 로그인ID면, false를 반환한다")
        void returnsFalseWhenLoginIdNotExists() {
            // arrange
            String nonExistingLoginId = "nonexisting";

            // act
            boolean exists = userRepository.existsByLoginId(nonExistingLoginId);

            // assert
            assertThat(exists).isFalse();
        }
    }
}