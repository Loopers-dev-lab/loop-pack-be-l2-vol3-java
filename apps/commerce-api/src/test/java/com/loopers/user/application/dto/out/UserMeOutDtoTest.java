package com.loopers.user.application.dto.out;

import com.loopers.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserMeOutDto 테스트")
class UserMeOutDtoTest {

	@Test
	@DisplayName("[UserMeOutDto.from()] User 도메인 객체 -> UserMeOutDto 변환. "
		+ "loginId, name, birthday, email이 정확히 매핑됨 (마스킹 없음)")
	void from() {
		// Arrange
		String loginId = "testuser01";
		String name = "홍길동";
		LocalDate birthday = LocalDate.of(1990, 1, 15);
		String email = "test@example.com";

		User user = User.reconstruct(1L, loginId, "encodedPassword", name, birthday, email);

		// Act
		UserMeOutDto outDto = UserMeOutDto.from(user);

		// Assert
		assertAll(
			() -> assertThat(outDto).isNotNull(),
			() -> assertThat(outDto.loginId()).isEqualTo(loginId),
			() -> assertThat(outDto.name()).isEqualTo(name),
			() -> assertThat(outDto.birthday()).isEqualTo(birthday),
			() -> assertThat(outDto.email()).isEqualTo(email)
		);
	}
}
