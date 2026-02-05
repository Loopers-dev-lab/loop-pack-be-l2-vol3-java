package com.loopers.support.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("ErrorResponse 테스트")
class ErrorResponseTest {

	@Test
	@DisplayName("[ErrorResponse.from()] ErrorType으로 생성 -> code, message가 ErrorType 값과 일치")
	void fromErrorType() {
		// Arrange
		ErrorType errorType = ErrorType.USER_ALREADY_EXISTS;

		// Act
		ErrorResponse response = ErrorResponse.from(errorType);

		// Assert
		assertAll(
			() -> assertThat(response.code()).isEqualTo(errorType.getCode()),
			() -> assertThat(response.message()).isEqualTo(errorType.getMessage())
		);
	}

	@Test
	@DisplayName("[ErrorResponse.of()] code, message 직접 지정 -> 전달한 값 그대로 반환")
	void ofCodeAndMessage() {
		// Arrange
		String code = "CUSTOM_CODE";
		String message = "커스텀 메시지";

		// Act
		ErrorResponse response = ErrorResponse.of(code, message);

		// Assert
		assertAll(
			() -> assertThat(response.code()).isEqualTo(code),
			() -> assertThat(response.message()).isEqualTo(message)
		);
	}
}
