package com.loopers.user.domain.model;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("User 도메인 모델 테스트")
class UserTest {

	private static final String VALID_LOGIN_ID = "testuser01";
	private static final String VALID_PASSWORD = "Test1234!";
	private static final String VALID_NAME = "홍길동";
	private static final LocalDate VALID_BIRTHDAY = LocalDate.of(1990, 1, 15);
	private static final String VALID_EMAIL = "test@example.com";

	@Nested
	@DisplayName("생성 테스트")
	class CreateTest {

		@Test
		@DisplayName("[User.create()] 유효한 정보로 User 생성 -> User 객체 반환. "
			+ "모든 필드가 정상적으로 설정됨")
		void createWithValidInfo() {
			// Act
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

			// Assert
			assertAll(
				() -> assertThat(user).isNotNull(),
				() -> assertThat(user.getLoginId()).isEqualTo(VALID_LOGIN_ID),
				() -> assertThat(user.getName()).isEqualTo(VALID_NAME),
				() -> assertThat(user.getBirthday()).isEqualTo(VALID_BIRTHDAY),
				() -> assertThat(user.getEmail()).isEqualTo(VALID_EMAIL)
			);
		}
	}

	@Nested
	@DisplayName("로그인 ID 검증 테스트")
	class LoginIdValidationTest {

		@ParameterizedTest
		@NullAndEmptySource
		@DisplayName("[User.create()] 로그인ID가 null 또는 빈 문자열 -> CoreException(ErrorType.INVALID_LOGIN_ID_FORMAT) 발생. "
			+ "에러 메시지: '로그인 ID는 영문과 숫자만 사용 가능하며, 1~20자여야 합니다.'")
		void failWhenLoginIdIsNullOrEmpty(String loginId) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(loginId, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT.getMessage())
			);
		}

		@Test
		@DisplayName("[User.create()] 로그인ID가 20자 초과 -> CoreException(ErrorType.INVALID_LOGIN_ID_FORMAT) 발생. "
			+ "에러 메시지: '로그인 ID는 영문과 숫자만 사용 가능하며, 1~20자여야 합니다.'")
		void failWhenLoginIdExceeds20Characters() {
			// Arrange
			String loginId = "a".repeat(21);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(loginId, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT.getMessage())
			);
		}

		@ParameterizedTest
		@ValueSource(strings = {"test_user", "test-user", "test user", "test@user", "테스트유저"})
		@DisplayName("[User.create()] 로그인ID에 영문/숫자 외 문자 포함 -> CoreException(ErrorType.INVALID_LOGIN_ID_FORMAT) 발생. "
			+ "에러 메시지: '로그인 ID는 영문과 숫자만 사용 가능하며, 1~20자여야 합니다.'")
		void failWhenLoginIdContainsInvalidCharacters(String loginId) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(loginId, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT.getMessage())
			);
		}
	}

	@Nested
	@DisplayName("이름 검증 테스트")
	class NameValidationTest {

		@ParameterizedTest
		@NullAndEmptySource
		@DisplayName("[User.create()] 이름이 null 또는 빈 문자열 -> CoreException(ErrorType.INVALID_NAME_FORMAT) 발생. "
			+ "에러 메시지: '이름은 한글 또는 영문만 사용 가능하며, 최대 100자입니다.'")
		void failWhenNameIsNullOrEmpty(String name) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(VALID_LOGIN_ID, VALID_PASSWORD, name, VALID_BIRTHDAY, VALID_EMAIL));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_NAME_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_NAME_FORMAT.getMessage())
			);
		}

		@Test
		@DisplayName("[User.create()] 이름이 100자 초과 -> CoreException(ErrorType.INVALID_NAME_FORMAT) 발생. "
			+ "에러 메시지: '이름은 한글 또는 영문만 사용 가능하며, 최대 100자입니다.'")
		void failWhenNameExceeds100Characters() {
			// Arrange
			String name = "가".repeat(101);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(VALID_LOGIN_ID, VALID_PASSWORD, name, VALID_BIRTHDAY, VALID_EMAIL));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_NAME_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_NAME_FORMAT.getMessage())
			);
		}

		@ParameterizedTest
		@ValueSource(strings = {"홍길동123", "Hong123", "홍길동!", "Hong Gildong"})
		@DisplayName("[User.create()] 이름에 숫자/특수문자/공백 포함 -> CoreException(ErrorType.INVALID_NAME_FORMAT) 발생. "
			+ "에러 메시지: '이름은 한글 또는 영문만 사용 가능하며, 최대 100자입니다.'")
		void failWhenNameContainsInvalidCharacters(String name) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(VALID_LOGIN_ID, VALID_PASSWORD, name, VALID_BIRTHDAY, VALID_EMAIL));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_NAME_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_NAME_FORMAT.getMessage())
			);
		}

		@Test
		@DisplayName("[User.create()] 한글 이름 -> User 객체 반환")
		void createWithKoreanName() {
			// Act
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, "홍길동", VALID_BIRTHDAY, VALID_EMAIL);

			// Assert
			assertThat(user.getName()).isEqualTo("홍길동");
		}

		@Test
		@DisplayName("[User.create()] 영문 이름 -> User 객체 반환")
		void createWithEnglishName() {
			// Act
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, "HongGildong", VALID_BIRTHDAY, VALID_EMAIL);

			// Assert
			assertThat(user.getName()).isEqualTo("HongGildong");
		}
	}

	@Nested
	@DisplayName("이메일 검증 테스트")
	class EmailValidationTest {

		@ParameterizedTest
		@NullAndEmptySource
		@DisplayName("[User.create()] 이메일이 null 또는 빈 문자열 -> CoreException(ErrorType.INVALID_EMAIL_FORMAT) 발생. "
			+ "에러 메시지: '올바른 이메일 형식이 아닙니다.'")
		void failWhenEmailIsNullOrEmpty(String email) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, email));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_EMAIL_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_EMAIL_FORMAT.getMessage())
			);
		}

		@Test
		@DisplayName("[User.create()] 이메일이 254자 초과 -> CoreException(ErrorType.INVALID_EMAIL_FORMAT) 발생. "
			+ "에러 메시지: '올바른 이메일 형식이 아닙니다.'")
		void failWhenEmailExceeds254Characters() {
			// Arrange
			String email = "a".repeat(243) + "@example.com";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, email));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_EMAIL_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_EMAIL_FORMAT.getMessage())
			);
		}

		@ParameterizedTest
		@ValueSource(strings = {"invalidemail", "invalid@", "@example.com", "invalid email@example.com"})
		@DisplayName("[User.create()] 잘못된 이메일 형식 -> CoreException(ErrorType.INVALID_EMAIL_FORMAT) 발생. "
			+ "에러 메시지: '올바른 이메일 형식이 아닙니다.'")
		void failWhenEmailFormatIsInvalid(String email) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, email));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_EMAIL_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_EMAIL_FORMAT.getMessage())
			);
		}

		@ParameterizedTest
		@ValueSource(strings = {"test@example..com", "test@.example.com", "test@example.com."})
		@DisplayName("[User.create()] 도메인에 연속점 또는 끝점 포함 -> CoreException(ErrorType.INVALID_EMAIL_FORMAT) 발생. "
			+ "에러 메시지: '올바른 이메일 형식이 아닙니다.'")
		void failWhenDomainContainsInvalidDots(String email) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, email));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_EMAIL_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_EMAIL_FORMAT.getMessage())
			);
		}
	}

	@Nested
	@DisplayName("생년월일 검증 테스트")
	class BirthdayValidationTest {

		@Test
		@DisplayName("[User.create()] 생년월일이 null -> CoreException(ErrorType.INVALID_BIRTHDAY) 발생. "
			+ "에러 메시지: '생년월일은 1900-01-01 이후, 오늘 이전이어야 합니다.'")
		void failWhenBirthdayIsNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, null, VALID_EMAIL));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTHDAY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_BIRTHDAY.getMessage())
			);
		}

		@Test
		@DisplayName("[User.create()] 미래 날짜 생년월일 -> CoreException(ErrorType.INVALID_BIRTHDAY) 발생. "
			+ "에러 메시지: '생년월일은 1900-01-01 이후, 오늘 이전이어야 합니다.'")
		void failWhenBirthdayIsFuture() {
			// Arrange
			LocalDate futureBirthday = LocalDate.now().plusDays(1);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, futureBirthday, VALID_EMAIL));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTHDAY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_BIRTHDAY.getMessage())
			);
		}

		@Test
		@DisplayName("[User.create()] 1900-01-01 이전 생년월일 -> CoreException(ErrorType.INVALID_BIRTHDAY) 발생. "
			+ "에러 메시지: '생년월일은 1900-01-01 이후, 오늘 이전이어야 합니다.'")
		void failWhenBirthdayBefore1900() {
			// Arrange
			LocalDate oldBirthday = LocalDate.of(1899, 12, 31);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, oldBirthday, VALID_EMAIL));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTHDAY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_BIRTHDAY.getMessage())
			);
		}
	}

	@Nested
	@DisplayName("재구성 테스트")
	class ReconstructTest {

		@Test
		@DisplayName("[User.reconstruct()] 저장된 데이터로 User 재구성 -> User 객체 반환. "
			+ "암호화된 비밀번호가 그대로 유지됨")
		void reconstructFromStoredData() {
			// Arrange
			Long id = 1L;
			String encodedPassword = "encodedPasswordValue";

			// Act
			User user = User.reconstruct(id, VALID_LOGIN_ID, encodedPassword, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

			// Assert
			assertAll(
				() -> assertThat(user.getId()).isEqualTo(id),
				() -> assertThat(user.getLoginId()).isEqualTo(VALID_LOGIN_ID),
				() -> assertThat(user.getPassword().value()).isEqualTo(encodedPassword),
				() -> assertThat(user.getName()).isEqualTo(VALID_NAME),
				() -> assertThat(user.getBirthday()).isEqualTo(VALID_BIRTHDAY),
				() -> assertThat(user.getEmail()).isEqualTo(VALID_EMAIL)
			);
		}
	}

	@Nested
	@DisplayName("비밀번호 변경 테스트")
	class ChangePasswordTest {

		@Test
		@DisplayName("[changePassword()] 유효한 현재 비밀번호와 새 비밀번호 -> 비밀번호 변경 성공. "
			+ "새 비밀번호로 authenticate 성공")
		void changePasswordSuccess() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
			String newPassword = "NewPass1234!";

			// Act
			user.changePassword(VALID_PASSWORD, newPassword);

			// Assert
			user.authenticate(newPassword);
		}

		@Test
		@DisplayName("[changePassword()] 현재 비밀번호 불일치 -> CoreException(ErrorType.UNAUTHORIZED) 발생. "
			+ "에러 메시지: '인증에 실패했습니다.'")
		void failWhenCurrentPasswordNotMatch() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> user.changePassword("WrongPass1!", "NewPass1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}

		@Test
		@DisplayName("[changePassword()] 새 비밀번호가 현재 비밀번호와 동일 -> CoreException(ErrorType.PASSWORD_SAME_AS_CURRENT) 발생. "
			+ "에러 메시지: '새 비밀번호는 현재 비밀번호와 같을 수 없습니다.'")
		void failWhenNewPasswordSameAsCurrent() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> user.changePassword(VALID_PASSWORD, VALID_PASSWORD));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT.getMessage())
			);
		}

		@Test
		@DisplayName("[changePassword()] 새 비밀번호 형식 오류 (8자 미만) -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenNewPasswordFormatInvalid() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> user.changePassword(VALID_PASSWORD, "short"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}

		@Test
		@DisplayName("[changePassword()] 새 비밀번호에 생년월일 포함 -> CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDAY) 발생. "
			+ "에러 메시지: '비밀번호에 생년월일을 포함할 수 없습니다.'")
		void failWhenNewPasswordContainsBirthday() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> user.changePassword(VALID_PASSWORD, "Aa19900115!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getMessage())
			);
		}
	}

	@Nested
	@DisplayName("인증 테스트")
	class AuthenticateTest {

		@Test
		@DisplayName("[authenticate()] 올바른 비밀번호 -> 예외 없이 성공")
		void authenticateSuccess() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

			// Act & Assert
			user.authenticate(VALID_PASSWORD);
		}

		@Test
		@DisplayName("[authenticate()] 잘못된 비밀번호 -> UNAUTHORIZED 예외")
		void authenticateFailWhenPasswordNotMatch() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> user.authenticate("WrongPass1!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}
	}
}
