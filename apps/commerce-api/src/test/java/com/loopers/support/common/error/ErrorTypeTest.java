package com.loopers.support.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("ErrorType 테스트")
class ErrorTypeTest {

	@ParameterizedTest(name = "[{index}] {0} -> status={1}, code={2}, message={3}")
	@MethodSource("errorTypeProvider")
	@DisplayName("[ErrorType] 모든 enum 상수의 status, code, message가 올바르게 설정됨")
	void allEnumConstantsHaveCorrectValues(ErrorType errorType, HttpStatus expectedStatus,
										   String expectedCode, String expectedMessage) {
		// Assert
		assertAll(
			() -> assertThat(errorType.getStatus()).isEqualTo(expectedStatus),
			() -> assertThat(errorType.getCode()).isEqualTo(expectedCode),
			() -> assertThat(errorType.getMessage()).isEqualTo(expectedMessage)
		);
	}

	static Stream<Arguments> errorTypeProvider() {
		return Stream.of(
			Arguments.of(ErrorType.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
				HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "일시적인 오류가 발생했습니다."),
			Arguments.of(ErrorType.BAD_REQUEST, HttpStatus.BAD_REQUEST,
				HttpStatus.BAD_REQUEST.getReasonPhrase(), "잘못된 요청입니다."),
			Arguments.of(ErrorType.NOT_FOUND, HttpStatus.NOT_FOUND,
				HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 요청입니다."),
			Arguments.of(ErrorType.CONFLICT, HttpStatus.CONFLICT,
				HttpStatus.CONFLICT.getReasonPhrase(), "이미 존재하는 리소스입니다."),
			Arguments.of(ErrorType.USER_ALREADY_EXISTS, HttpStatus.CONFLICT,
				"USER_ALREADY_EXISTS", "이미 가입된 로그인 ID입니다."),
			Arguments.of(ErrorType.INVALID_PASSWORD_FORMAT, HttpStatus.BAD_REQUEST,
				"INVALID_PASSWORD_FORMAT", "비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다."),
			Arguments.of(ErrorType.PASSWORD_CONTAINS_BIRTHDAY, HttpStatus.BAD_REQUEST,
				"PASSWORD_CONTAINS_BIRTHDAY", "비밀번호에 생년월일을 포함할 수 없습니다."),
			Arguments.of(ErrorType.INVALID_LOGIN_ID_FORMAT, HttpStatus.BAD_REQUEST,
				"INVALID_LOGIN_ID_FORMAT", "로그인 ID는 영문과 숫자만 사용 가능하며, 1~20자여야 합니다."),
			Arguments.of(ErrorType.INVALID_NAME_FORMAT, HttpStatus.BAD_REQUEST,
				"INVALID_NAME_FORMAT", "이름은 한글 또는 영문만 사용 가능하며, 최대 100자입니다."),
			Arguments.of(ErrorType.INVALID_EMAIL_FORMAT, HttpStatus.BAD_REQUEST,
				"INVALID_EMAIL_FORMAT", "올바른 이메일 형식이 아닙니다."),
			Arguments.of(ErrorType.INVALID_BIRTHDAY, HttpStatus.BAD_REQUEST,
				"INVALID_BIRTHDAY", "생년월일은 1900-01-01 이후, 오늘 이전이어야 합니다."),
			Arguments.of(ErrorType.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
				"UNAUTHORIZED", "인증에 실패했습니다.")
		);
	}

	@Test
	@DisplayName("[ErrorType] enum 상수 개수가 12개임을 보장")
	void enumConstantCount() {
		// Assert
		assertThat(ErrorType.values()).hasSize(12);
	}
}
