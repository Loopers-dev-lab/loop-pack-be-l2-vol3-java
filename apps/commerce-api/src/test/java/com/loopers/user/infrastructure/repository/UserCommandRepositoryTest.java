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

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

	@Test
	@DisplayName("[UserCommandRepository.update()] 존재하지 않는 ID로 수정 시도 -> CoreException(NOT_FOUND) 발생. "
		+ "DB에 없는 ID를 가진 User를 update하면 NOT_FOUND 예외")
	void updateUserWithNonExistentIdThrowsNotFound() {
		// Arrange
		User user = User.reconstruct(
			999L,
			"testuser01",
			"encoded_password",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);

		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userCommandRepository.update(user));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.NOT_FOUND.getMessage())
		);
	}
}
