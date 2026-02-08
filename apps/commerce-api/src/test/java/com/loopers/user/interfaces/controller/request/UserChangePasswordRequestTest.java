package com.loopers.user.interfaces.controller.request;

import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserChangePasswordRequest 테스트")
class UserChangePasswordRequestTest {

	@Test
	@DisplayName("[UserChangePasswordRequest.toInDto()] 유효한 Request -> UserChangePasswordInDto 변환. "
		+ "currentPassword, newPassword 필드가 정확히 매핑됨")
	void toInDto() {
		// Arrange
		String currentPassword = "Test1234!";
		String newPassword = "NewPass1234!";

		UserChangePasswordRequest request = new UserChangePasswordRequest(currentPassword, newPassword);

		// Act
		UserChangePasswordInDto inDto = request.toInDto();

		// Assert
		assertAll(
			() -> assertThat(inDto).isNotNull(),
			() -> assertThat(inDto.currentPassword()).isEqualTo(currentPassword),
			() -> assertThat(inDto.newPassword()).isEqualTo(newPassword)
		);
	}
}
