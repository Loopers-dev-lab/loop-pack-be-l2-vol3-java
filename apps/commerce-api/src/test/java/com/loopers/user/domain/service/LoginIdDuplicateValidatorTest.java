package com.loopers.user.domain.service;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("LoginIdDuplicateValidator 테스트")
class LoginIdDuplicateValidatorTest {

	@SuppressWarnings("unchecked")
	private final Predicate<String> existsByLoginId = mock(Predicate.class);

	private LoginIdDuplicateValidator validator;

	@BeforeEach
	void setUp() {
		validator = new LoginIdDuplicateValidator(existsByLoginId);
	}

	@Test
	@DisplayName("[validate()] 존재하지 않는 loginId -> 예외 없이 통과. "
		+ "Predicate가 false를 반환하면 정상 처리")
	void validatePassesWhenLoginIdDoesNotExist() {
		// Arrange
		given(existsByLoginId.test("newuser01")).willReturn(false);

		// Act & Assert
		assertDoesNotThrow(() -> validator.validate("newuser01"));
	}

	@Test
	@DisplayName("[validate()] 이미 존재하는 loginId -> CoreException(USER_ALREADY_EXISTS) 발생. "
		+ "에러 메시지: '이미 가입된 로그인 ID입니다.'")
	void validateThrowsWhenLoginIdAlreadyExists() {
		// Arrange
		given(existsByLoginId.test("existinguser")).willReturn(true);

		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> validator.validate("existinguser"));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
		);
	}

	@Test
	@DisplayName("[validate()] loginId 전달 검증 -> Predicate에 loginId가 정확히 전달됨. "
		+ "validate 호출 시 전달된 loginId로 Predicate.test()가 호출되는지 확인")
	void validatePassesLoginIdToPredicate() {
		// Arrange
		given(existsByLoginId.test("testuser01")).willReturn(false);

		// Act
		validator.validate("testuser01");

		// Assert
		verify(existsByLoginId).test("testuser01");
	}

	@Test
	@DisplayName("[validate()] null loginId -> CoreException(INVALID_LOGIN_ID_FORMAT) 발생. "
		+ "null 입력에 대해 Predicate 호출 없이 즉시 예외 발생")
	void validateThrowsWhenLoginIdIsNull() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> validator.validate(null));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT.getMessage())
		);
	}

	@Test
	@DisplayName("[validate()] blank loginId -> CoreException(INVALID_LOGIN_ID_FORMAT) 발생. "
		+ "빈 문자열/공백 입력에 대해 Predicate 호출 없이 즉시 예외 발생")
	void validateThrowsWhenLoginIdIsBlank() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> validator.validate("   "));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT.getMessage())
		);
	}
}
