package com.loopers.user.infrastructure.repository;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("UserQueryRepository 테스트")
class UserQueryRepositoryTest {

	@Autowired
	private UserQueryRepository userQueryRepository;

	@Autowired
	private UserCommandRepository userCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}

	@Nested
	@DisplayName("조회 테스트")
	class FindTest {

		@Test
		@DisplayName("[UserQueryRepository.findByLoginId()] 존재하는 loginId 조회 -> User 반환")
		void findByLoginId() {
			// Arrange
			User user = User.create(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			userCommandRepository.save(user);

			// Act
			Optional<User> foundUser = userQueryRepository.findByLoginId("testuser01");

			// Assert
			assertAll(
				() -> assertThat(foundUser).isPresent(),
				() -> assertThat(foundUser.get().getLoginId()).isEqualTo("testuser01"),
				() -> assertThat(foundUser.get().getName()).isEqualTo("홍길동")
			);
		}

		@Test
		@DisplayName("[UserQueryRepository.findByLoginId()] 존재하지 않는 loginId 조회 -> Optional.empty() 반환")
		void findByLoginIdNotFound() {
			// Act
			Optional<User> foundUser = userQueryRepository.findByLoginId("nonexistent");

			// Assert
			assertThat(foundUser).isEmpty();
		}
	}

	@Nested
	@DisplayName("존재 여부 확인 테스트")
	class ExistsTest {

		@Test
		@DisplayName("[UserQueryRepository.existsByLoginId()] 존재하는 loginId -> true 반환")
		void existsByLoginIdTrue() {
			// Arrange
			User user = User.create(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			userCommandRepository.save(user);

			// Act
			boolean exists = userQueryRepository.existsByLoginId("testuser01");

			// Assert
			assertThat(exists).isTrue();
		}

		@Test
		@DisplayName("[UserQueryRepository.existsByLoginId()] 존재하지 않는 loginId -> false 반환")
		void existsByLoginIdFalse() {
			// Act
			boolean exists = userQueryRepository.existsByLoginId("nonexistent");

			// Assert
			assertThat(exists).isFalse();
		}
	}

	@Nested
	@DisplayName("비밀번호 매칭 테스트")
	class PasswordMatchTest {

		@Test
		@DisplayName("[UserQueryRepository.findByLoginId()] 조회된 User의 비밀번호 매칭 -> true 반환")
		void matchPasswordAfterSave() {
			// Arrange
			String rawPassword = "Test1234!";
			User user = User.create(
				"testuser01",
				rawPassword,
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			userCommandRepository.save(user);

			// Act
			Optional<User> foundUser = userQueryRepository.findByLoginId("testuser01");

			// Assert
			assertAll(
				() -> assertThat(foundUser).isPresent(),
				() -> assertThat(foundUser.get().getPassword().matches(rawPassword)).isTrue()
			);
		}
	}
}
