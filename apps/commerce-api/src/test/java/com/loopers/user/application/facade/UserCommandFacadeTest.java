package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.application.service.UserQueryService;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.service.LoginIdDuplicateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandFacade 테스트")
class UserCommandFacadeTest {

	@Mock
	private UserCommandService userCommandService;

	@Mock
	private UserQueryService userQueryService;

	@Mock
	private LoginIdDuplicateValidator loginIdDuplicateValidator;

	private UserCommandFacade userCommandFacade;

	@BeforeEach
	void setUp() {
		userCommandFacade = new UserCommandFacade(userCommandService, userQueryService, loginIdDuplicateValidator);
	}

	@Nested
	@DisplayName("회원가입 테스트")
	class SignUpTest {

		@Test
		@DisplayName("[UserCommandFacade.signUp()] 유효한 회원가입 정보 -> UserSignUpOutDto 반환. "
			+ "LoginIdDuplicateValidator로 중복 체크 후 CommandService로 저장")
		void signUpSuccess() {
			// Arrange
			UserSignUpInDto inDto = new UserSignUpInDto(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			willDoNothing().given(loginIdDuplicateValidator).validate("testuser01");
			given(userCommandService.createUser(any(User.class))).willAnswer(invocation -> {
				User user = invocation.getArgument(0);
				return User.reconstruct(
					1L,
					user.getLoginId(),
					user.getPassword().value(),
					user.getName(),
					user.getBirthday(),
					user.getEmail()
				);
			});

			// Act
			UserSignUpOutDto result = userCommandFacade.signUp(inDto);

			// Assert
			assertAll(
				() -> assertThat(result).isNotNull(),
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.loginId()).isEqualTo("testuser01"),
				() -> assertThat(result.name()).isEqualTo("홍길동"),
				() -> assertThat(result.email()).isEqualTo("test@example.com")
			);
			verify(loginIdDuplicateValidator).validate("testuser01");
			verify(userCommandService).createUser(any(User.class));
		}

		@Test
		@DisplayName("[UserCommandFacade.signUp()] 중복된 로그인 ID -> CoreException(ErrorType.USER_ALREADY_EXISTS) 발생. "
			+ "에러 메시지: '이미 가입된 로그인 ID입니다.'")
		void signUpFailWhenLoginIdAlreadyExists() {
			// Arrange
			UserSignUpInDto inDto = new UserSignUpInDto(
				"existinguser",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			willThrow(new CoreException(ErrorType.USER_ALREADY_EXISTS))
				.given(loginIdDuplicateValidator).validate("existinguser");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.signUp(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
			);
			verify(loginIdDuplicateValidator).validate("existinguser");
			verify(userCommandService, never()).createUser(any(User.class));
		}

		@Test
		@DisplayName("[UserCommandFacade.signUp()] 대문자/공백 변형 loginId가 정규화 후 중복 -> CoreException(USER_ALREADY_EXISTS)")
		void signUpFailWhenNormalizedLoginIdAlreadyExists() {
			// Arrange
			UserCommandFacade facadeWithRealValidator = new UserCommandFacade(
				userCommandService,
				userQueryService,
				new LoginIdDuplicateValidator(loginId -> "testuser01".equals(loginId))
			);
			UserSignUpInDto inDto = new UserSignUpInDto(
				"  TESTUSER01  ",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> facadeWithRealValidator.signUp(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
			);
			verify(userCommandService, never()).createUser(any(User.class));
		}
	}

	@Nested
	@DisplayName("비밀번호 변경 테스트")
	class ChangePasswordTest {

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 유효한 인증 및 비밀번호 변경 요청 -> 정상 완료. "
			+ "인증 후 changePassword 호출, updateUser로 저장")
		void changePasswordSuccess() {
			// Arrange
			User user = User.create("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "NewPass1234!");

			given(userQueryService.findByLoginId("testuser01")).willReturn(Optional.of(user));
			given(userCommandService.updateUser(any(User.class))).willReturn(user);

			// Act & Assert
			assertDoesNotThrow(() ->
				userCommandFacade.changePassword("testuser01", "Test1234!", inDto));

			verify(userQueryService).findByLoginId("testuser01");
			verify(userCommandService).updateUser(any(User.class));
		}

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] loginId 대문자/공백 포함 헤더 -> trim + lowercase 후 정상 조회")
		void changePasswordNormalizesLoginIdHeader() {
			// Arrange
			User user = User.create("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "NewPass1234!");

			given(userQueryService.findByLoginId("testuser01")).willReturn(Optional.of(user));
			given(userCommandService.updateUser(any(User.class))).willReturn(user);

			// Act & Assert
			assertDoesNotThrow(() ->
				userCommandFacade.changePassword("  TESTUSER01  ", "Test1234!", inDto));

			verify(userQueryService).findByLoginId("testuser01");
			verify(userCommandService).updateUser(any(User.class));
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  ", "\t"})
		@DisplayName("[UserCommandFacade.changePassword()] loginId 헤더 null/blank -> CoreException(UNAUTHORIZED). "
			+ "인증 실패: 로그인 ID 미제공")
		void failWhenLoginIdHeaderIsNullOrBlank(String loginId) {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "NewPass1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword(loginId, "Test1234!", inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  ", "\t"})
		@DisplayName("[UserCommandFacade.changePassword()] password 헤더 null/blank -> CoreException(UNAUTHORIZED). "
			+ "인증 실패: 비밀번호 미제공")
		void failWhenPasswordHeaderIsNullOrBlank(String password) {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "NewPass1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword("testuser01", password, inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 존재하지 않는 사용자 -> CoreException(UNAUTHORIZED). "
			+ "인증 실패: 사용자 미존재")
		void failWhenUserNotFound() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "NewPass1234!");

			given(userQueryService.findByLoginId("nonexistent")).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword("nonexistent", "Test1234!", inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 헤더 비밀번호 불일치 -> CoreException(UNAUTHORIZED). "
			+ "인증 실패: 비밀번호 불일치")
		void failWhenHeaderPasswordNotMatch() {
			// Arrange
			User user = User.create("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "NewPass1234!");

			given(userQueryService.findByLoginId("testuser01")).willReturn(Optional.of(user));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword("testuser01", "WrongPass1!", inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 현재/새 비밀번호 동일 -> CoreException(PASSWORD_SAME_AS_CURRENT)")
		void failWhenNewPasswordSameAsCurrent() {
			// Arrange
			User user = User.create("testuser01", "Test1234!", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com");

			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "Test1234!");

			given(userQueryService.findByLoginId("testuser01")).willReturn(Optional.of(user));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword("testuser01", "Test1234!", inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT.getMessage())
			);
			verify(userCommandService, never()).updateUser(any(User.class));
		}
	}
}
