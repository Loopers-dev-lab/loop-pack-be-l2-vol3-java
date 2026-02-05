package com.loopers.user.infrastructure.entity;

import com.loopers.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserEntity 테스트")
class UserEntityTest {

	private static final String VALID_LOGIN_ID = "testuser01";
	private static final String VALID_PASSWORD = "Test1234!";
	private static final String VALID_NAME = "홍길동";
	private static final LocalDate VALID_BIRTHDAY = LocalDate.of(1990, 1, 15);
	private static final String VALID_EMAIL = "test@example.com";

	@Nested
	@DisplayName("도메인 -> 엔티티 변환 테스트")
	class FromDomainTest {

		@Test
		@DisplayName("[from()] User 도메인 -> UserEntity 변환. "
			+ "loginId, password, name, birthday, email이 정확히 매핑됨")
		void fromDomain() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

			// Act
			UserEntity entity = UserEntity.from(user);

			// Assert
			assertAll(
				() -> assertThat(entity).isNotNull(),
				() -> assertThat(entity.getLoginId()).isEqualTo(VALID_LOGIN_ID),
				() -> assertThat(entity.getPassword()).isEqualTo(user.getPassword().value()),
				() -> assertThat(entity.getName()).isEqualTo(VALID_NAME),
				() -> assertThat(entity.getBirthday()).isEqualTo(VALID_BIRTHDAY),
				() -> assertThat(entity.getEmail()).isEqualTo(VALID_EMAIL)
			);
		}
	}

	@Nested
	@DisplayName("엔티티 -> 도메인 변환 테스트")
	class ToDomainTest {

		@Test
		@DisplayName("[toDomain()] UserEntity -> User 도메인 변환. "
			+ "User.reconstruct()를 통해 id, loginId, password, name, birthday, email이 정확히 복원됨")
		void toDomain() {
			// Arrange
			User originalUser = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
			UserEntity entity = UserEntity.from(originalUser);

			// Act
			User reconstructedUser = entity.toDomain();

			// Assert
			assertAll(
				() -> assertThat(reconstructedUser).isNotNull(),
				() -> assertThat(reconstructedUser.getId()).isEqualTo(entity.getId()),
				() -> assertThat(reconstructedUser.getLoginId()).isEqualTo(entity.getLoginId()),
				() -> assertThat(reconstructedUser.getPassword().value()).isEqualTo(entity.getPassword()),
				() -> assertThat(reconstructedUser.getName()).isEqualTo(entity.getName()),
				() -> assertThat(reconstructedUser.getBirthday()).isEqualTo(entity.getBirthday()),
				() -> assertThat(reconstructedUser.getEmail()).isEqualTo(entity.getEmail())
			);
		}
	}
}
