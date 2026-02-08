package com.loopers.user.domain.model.vo;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Password 값 객체 테스트")
class PasswordTest {

	private static final LocalDate DEFAULT_BIRTHDAY = LocalDate.of(1990, 1, 15);

	@Nested
	@DisplayName("생성 테스트")
	class CreateTest {

		@Test
		@DisplayName("[Password.create()] 유효한 비밀번호로 생성 -> Password 객체 반환. "
			+ "비밀번호는 SHA-256 해싱 및 Base64 인코딩되어 저장됨")
		void createWithValidPassword() {
			// Arrange
			String rawPassword = "Test1234!";

			// Act
			Password password = Password.create(rawPassword, DEFAULT_BIRTHDAY);

			// Assert
			assertAll(
				() -> assertThat(password).isNotNull(),
				() -> assertThat(password.value()).isNotEqualTo(rawPassword)
			);
		}

		@Test
		@DisplayName("[Password.create()] 8자 비밀번호(최소 유효) -> Password 객체 반환")
		void createWithMinimumLengthPassword() {
			// Arrange
			String rawPassword = "Aa1!aaaa";

			// Act
			Password password = Password.create(rawPassword, DEFAULT_BIRTHDAY);

			// Assert
			assertAll(
				() -> assertThat(password).isNotNull(),
				() -> assertThat(password.matches(rawPassword)).isTrue()
			);
		}

		@Test
		@DisplayName("[Password.create()] 16자 비밀번호(최대 유효) -> Password 객체 반환")
		void createWithMaximumLengthPassword() {
			// Arrange
			String rawPassword = "Aa1!aaaaaaaaaaaa";

			// Act
			Password password = Password.create(rawPassword, DEFAULT_BIRTHDAY);

			// Assert
			assertAll(
				() -> assertThat(password).isNotNull(),
				() -> assertThat(password.matches(rawPassword)).isTrue()
			);
		}

		@ParameterizedTest
		@ValueSource(strings = {"Test12!", "Test1!"})
		@DisplayName("[Password.create()] 8자 미만 비밀번호 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenPasswordLessThan8Characters(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.create(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}

		@Test
		@DisplayName("[Password.create()] 16자 초과 비밀번호 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenPasswordMoreThan16Characters() {
			// Arrange
			String rawPassword = "Test1234567890!@#";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.create(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}

		@ParameterizedTest
		@ValueSource(strings = {"test1234!", "TEST1234!", "Testtest!", "Test12345", "Test!@#$%"})
		@DisplayName("[Password.create()] 영문 대소문자/숫자/특수문자 누락 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenMissingRequiredCharacters(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.create(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}

		@Test
		@DisplayName("[Password.create()] 생년월일(YYYYMMDD) 포함 -> CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDAY) 발생. "
			+ "에러 메시지: '비밀번호에 생년월일을 포함할 수 없습니다.'")
		void failWhenContainsBirthdayYYYYMMDD() {
			// Arrange
			String rawPassword = "Aa19900115!";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.create(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getMessage())
			);
		}

		@Test
		@DisplayName("[Password.create()] 생년월일(YYMMDD) 포함 -> CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDAY) 발생. "
			+ "에러 메시지: '비밀번호에 생년월일을 포함할 수 없습니다.'")
		void failWhenContainsBirthdayYYMMDD() {
			// Arrange
			String rawPassword = "Aa900115!@";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.create(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getMessage())
			);
		}

		@ParameterizedTest
		@ValueSource(strings = {"Test 1234!", "Test\t1234!", "Test\n1234!"})
		@DisplayName("[Password.create()] 비밀번호에 공백/탭/개행 포함 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "허용 문자 외 공백 문자 차단")
		void failWhenPasswordContainsWhitespace(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.create(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}

		@ParameterizedTest
		@ValueSource(strings = {"Test1234!한글", "Tëst1234!"})
		@DisplayName("[Password.create()] 비밀번호에 비ASCII 문자 포함 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "허용 문자 외 한글/특수 유니코드 차단")
		void failWhenPasswordContainsNonAscii(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.create(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}

		@Test
		@DisplayName("[Password.create()] 생년월일(YYYY-MM-DD) 포함 -> CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDAY) 발생. "
			+ "에러 메시지: '비밀번호에 생년월일을 포함할 수 없습니다.'")
		void failWhenContainsBirthdayWithDashes() {
			// Arrange
			String rawPassword = "Aa1990-01-15!";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.create(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getMessage())
			);
		}

		@Test
		@DisplayName("[Password.create()] 비밀번호가 null -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenPasswordIsNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.create(null, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}
	}

	@Nested
	@DisplayName("비밀번호 매칭 테스트")
	class MatchTest {

		@Test
		@DisplayName("[Password.matches()] 원본 비밀번호와 매칭 -> true 반환")
		void matchWithRawPassword() {
			// Arrange
			String rawPassword = "Test1234!";
			Password password = Password.create(rawPassword, DEFAULT_BIRTHDAY);

			// Act
			boolean matches = password.matches(rawPassword);

			// Assert
			assertThat(matches).isTrue();
		}

		@Test
		@DisplayName("[Password.matches()] 다른 비밀번호와 매칭 -> false 반환")
		void notMatchWithDifferentPassword() {
			// Arrange
			String rawPassword = "Test1234!";
			Password password = Password.create(rawPassword, DEFAULT_BIRTHDAY);

			// Act
			boolean matches = password.matches("Wrong1234!");

			// Assert
			assertThat(matches).isFalse();
		}
	}

	@Nested
	@DisplayName("fromEncoded 테스트")
	class FromEncodedTest {

		@Test
		@DisplayName("[Password.fromEncoded()] 암호화된 비밀번호로 Password 객체 생성 -> 원본 비밀번호와 매칭 성공")
		void createFromEncodedPassword() {
			// Arrange
			String rawPassword = "Test1234!";
			Password original = Password.create(rawPassword, DEFAULT_BIRTHDAY);
			String encodedValue = original.value();

			// Act
			Password restored = Password.fromEncoded(encodedValue);

			// Assert
			assertAll(
				() -> assertThat(restored.value()).isEqualTo(encodedValue),
				() -> assertThat(restored.matches(rawPassword)).isTrue()
			);
		}
	}
}
