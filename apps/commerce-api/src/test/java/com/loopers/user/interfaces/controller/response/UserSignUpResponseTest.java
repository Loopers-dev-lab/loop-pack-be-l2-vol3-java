package com.loopers.user.interfaces.controller.response;

import com.loopers.user.application.dto.out.UserSignUpOutDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserSignUpResponse 테스트")
class UserSignUpResponseTest {

	@Test
	@DisplayName("[UserSignUpResponse.from()] UserSignUpOutDto -> UserSignUpResponse 변환. "
		+ "id, loginId, name, birthday, email이 정확히 매핑됨")
	void from() {
		// Arrange
		Long id = 1L;
		String loginId = "testuser01";
		String name = "홍길동";
		LocalDate birthday = LocalDate.of(1990, 1, 15);
		String email = "test@example.com";

		UserSignUpOutDto outDto = new UserSignUpOutDto(id, loginId, name, birthday, email);

		// Act
		UserSignUpResponse response = UserSignUpResponse.from(outDto);

		// Assert
		assertAll(
			() -> assertThat(response).isNotNull(),
			() -> assertThat(response.id()).isEqualTo(id),
			() -> assertThat(response.loginId()).isEqualTo(loginId),
			() -> assertThat(response.name()).isEqualTo(name),
			() -> assertThat(response.birthday()).isEqualTo(birthday),
			() -> assertThat(response.email()).isEqualTo(email)
		);
	}
}
