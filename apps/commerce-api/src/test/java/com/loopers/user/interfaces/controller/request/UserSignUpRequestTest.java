package com.loopers.user.interfaces.controller.request;

import com.loopers.user.application.dto.in.UserSignUpInDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserSignUpRequest 테스트")
class UserSignUpRequestTest {

	@Test
	@DisplayName("[UserSignUpRequest.toInDto()] 유효한 Request -> UserSignUpInDto 변환. "
		+ "모든 필드(loginId, password, name, birthday, email)가 정확히 매핑됨")
	void toInDto() {
		// Arrange
		String loginId = "testuser01";
		String password = "Test1234!";
		String name = "홍길동";
		LocalDate birthday = LocalDate.of(1990, 1, 15);
		String email = "test@example.com";

		UserSignUpRequest request = new UserSignUpRequest(loginId, password, name, birthday, email);

		// Act
		UserSignUpInDto inDto = request.toInDto();

		// Assert
		assertAll(
			() -> assertThat(inDto).isNotNull(),
			() -> assertThat(inDto.loginId()).isEqualTo(loginId),
			() -> assertThat(inDto.password()).isEqualTo(password),
			() -> assertThat(inDto.name()).isEqualTo(name),
			() -> assertThat(inDto.birthday()).isEqualTo(birthday),
			() -> assertThat(inDto.email()).isEqualTo(email)
		);
	}
}
