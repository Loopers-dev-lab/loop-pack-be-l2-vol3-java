package com.loopers.user.infrastructure.repository;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("UserCommandRepository 테스트")
class UserCommandRepositoryTest {

	@Autowired
	private UserCommandRepository userCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}

	@Test
	@DisplayName("[UserCommandRepository.save()] 유효한 User 저장 -> ID가 할당된 User 반환")
	void saveUser() {
		// Arrange
		User user = User.create(
			"testuser01",
			"Test1234!",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);

		// Act
		User savedUser = userCommandRepository.save(user);

		// Assert
		assertAll(
			() -> assertThat(savedUser.getId()).isNotNull(),
			() -> assertThat(savedUser.getLoginId()).isEqualTo("testuser01"),
			() -> assertThat(savedUser.getName()).isEqualTo("홍길동")
		);
	}
}
