package com.loopers.user.support.common;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("HeaderValidator 테스트")
class HeaderValidatorTest {

	@Test
	@DisplayName("[HeaderValidator.validate()] 유효한 loginId, password -> 예외 없이 통과")
	void validateSuccess() {
		// Act & Assert
		assertDoesNotThrow(() -> HeaderValidator.validate("testuser01", "Test1234!"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"  ", "\t"})
	@DisplayName("[HeaderValidator.validate()] loginId가 null/blank -> CoreException(UNAUTHORIZED)")
	void validateFailWhenLoginIdNullOrBlank(String loginId) {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> HeaderValidator.validate(loginId, "Test1234!"));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
		);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"  ", "\t"})
	@DisplayName("[HeaderValidator.validate()] password가 null/blank -> CoreException(UNAUTHORIZED)")
	void validateFailWhenPasswordNullOrBlank(String password) {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> HeaderValidator.validate("testuser01", password));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
		);
	}
}
