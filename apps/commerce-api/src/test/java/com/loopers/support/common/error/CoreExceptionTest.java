package com.loopers.support.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("CoreException 테스트")
class CoreExceptionTest {

	@Nested
	@DisplayName("생성자 테스트")
	class ConstructorTest {

		@Test
		@DisplayName("[CoreException(ErrorType)] ErrorType만으로 생성 -> getMessage()가 ErrorType.getMessage() 반환. customMessage는 null")
		void createWithErrorTypeOnly() {
			// Arrange
			ErrorType errorType = ErrorType.BAD_REQUEST;

			// Act
			CoreException exception = new CoreException(errorType);

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
				() -> assertThat(exception.getMessage()).isEqualTo(errorType.getMessage()),
				() -> assertThat(exception.getCustomMessage()).isNull()
			);
		}

		@Test
		@DisplayName("[CoreException(ErrorType, String)] customMessage 포함 생성 -> getMessage()가 customMessage 반환")
		void createWithCustomMessage() {
			// Arrange
			ErrorType errorType = ErrorType.BAD_REQUEST;
			String customMessage = "커스텀 에러 메시지";

			// Act
			CoreException exception = new CoreException(errorType, customMessage);

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
				() -> assertThat(exception.getMessage()).isEqualTo(customMessage),
				() -> assertThat(exception.getCustomMessage()).isEqualTo(customMessage)
			);
		}

		@Test
		@DisplayName("[CoreException(ErrorType, null)] customMessage가 null -> getMessage()가 ErrorType.getMessage() 반환")
		void createWithNullCustomMessage() {
			// Arrange
			ErrorType errorType = ErrorType.INTERNAL_ERROR;

			// Act
			CoreException exception = new CoreException(errorType, null);

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR),
				() -> assertThat(exception.getMessage()).isEqualTo(errorType.getMessage()),
				() -> assertThat(exception.getCustomMessage()).isNull()
			);
		}
	}

	@Nested
	@DisplayName("Getter 테스트")
	class GetterTest {

		@Test
		@DisplayName("[getErrorType()] 생성 시 전달한 ErrorType 반환")
		void getErrorType() {
			// Arrange
			ErrorType errorType = ErrorType.USER_ALREADY_EXISTS;

			// Act
			CoreException exception = new CoreException(errorType);

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS);
		}

		@Test
		@DisplayName("[getCustomMessage()] customMessage가 있으면 해당 값 반환")
		void getCustomMessageWhenPresent() {
			// Arrange
			String customMessage = "상세 에러 메시지";

			// Act
			CoreException exception = new CoreException(ErrorType.BAD_REQUEST, customMessage);

			// Assert
			assertThat(exception.getCustomMessage()).isEqualTo(customMessage);
		}

		@Test
		@DisplayName("[getCustomMessage()] customMessage가 없으면 null 반환")
		void getCustomMessageWhenAbsent() {
			// Act
			CoreException exception = new CoreException(ErrorType.BAD_REQUEST);

			// Assert
			assertThat(exception.getCustomMessage()).isNull();
		}
	}
}
