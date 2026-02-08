package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.application.service.UserQueryService;
import com.loopers.user.domain.model.User;
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

	private UserCommandFacade userCommandFacade;

	@BeforeEach
	void setUp() {
		userCommandFacade = new UserCommandFacade(userCommandService, userQueryService);
	}

	@Nested
	@DisplayName("회원가입 테스트")
	class SignUpTest {

		@Test
		@DisplayName("[UserCommandFacade.signUp()] 유효한 회원가입 정보 -> UserSignUpOutDto 반환. "
			+ "정규화된 loginId로 중복 체크 후 CommandService로 저장")
		void signUpSuccess() {
			// Arrange
			UserSignUpInDto inDto = new UserSignUpInDto(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			given(userQueryService.existsByLoginId("testuser01")).willReturn(false);
			given(userCommandService.createUser(any(UserSignUpInDto.class))).willAnswer(invocation -> {
				UserSignUpInDto request = invocation.getArgument(0);
				User user = User.create(
					request.loginId(),
					request.password(),
					request.name(),
					request.birthday(),
					request.email()
				);
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
			verify(userQueryService).existsByLoginId("testuser01");
			verify(userCommandService).createUser(inDto);
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

			given(userQueryService.existsByLoginId("existinguser")).willReturn(true);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.signUp(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
			);
			verify(userQueryService).existsByLoginId("existinguser");
			verify(userCommandService, never()).createUser(any(UserSignUpInDto.class));
		}

		@Test
		@DisplayName("[UserCommandFacade.signUp()] 대문자/공백 변형 loginId가 정규화 후 중복 -> CoreException(USER_ALREADY_EXISTS)")
		void signUpFailWhenNormalizedLoginIdAlreadyExists() {
			// Arrange
			UserSignUpInDto inDto = new UserSignUpInDto(
				"  TESTUSER01  ",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			given(userQueryService.existsByLoginId("testuser01")).willReturn(true);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.signUp(inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
			);
			verify(userQueryService).existsByLoginId("testuser01");
			verify(userCommandService, never()).createUser(any(UserSignUpInDto.class));
		}
	}

	@Nested
	@DisplayName("비밀번호 변경 테스트")
	class ChangePasswordTest {

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 유효한 인증 및 비밀번호 변경 요청 -> 정상 완료. "
			+ "Header 검증 후 UserCommandService.updatePassword()로 위임")
		void changePasswordSuccess() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "NewPass1234!");
			willDoNothing().given(userCommandService)
				.updatePassword("Test1234!", "testuser01", "Test1234!", "NewPass1234!");

			// Act & Assert
			assertDoesNotThrow(() ->
				userCommandFacade.changePassword("testuser01", "Test1234!", inDto));

			verify(userCommandService)
				.updatePassword("Test1234!", "testuser01", "Test1234!", "NewPass1234!");
		}

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] loginId 원문은 그대로 서비스에 전달")
		void changePasswordPassesRawLoginIdToService() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "NewPass1234!");
			willDoNothing().given(userCommandService)
				.updatePassword("Test1234!", "  TESTUSER01  ", "Test1234!", "NewPass1234!");

			// Act & Assert
			assertDoesNotThrow(() ->
				userCommandFacade.changePassword("  TESTUSER01  ", "Test1234!", inDto));

			verify(userCommandService)
				.updatePassword("Test1234!", "  TESTUSER01  ", "Test1234!", "NewPass1234!");
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
			verify(userCommandService, never()).updatePassword(any(), any(), any(), any());
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
			verify(userCommandService, never()).updatePassword(any(), any(), any(), any());
		}

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 존재하지 않는 사용자 -> CoreException(UNAUTHORIZED). "
			+ "서비스 예외를 그대로 전파")
		void failWhenUserNotFound() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "NewPass1234!");
			willThrow(new CoreException(ErrorType.UNAUTHORIZED)).given(userCommandService)
				.updatePassword("Test1234!", "nonexistent", "Test1234!", "NewPass1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword("nonexistent", "Test1234!", inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userCommandService)
				.updatePassword("Test1234!", "nonexistent", "Test1234!", "NewPass1234!");
		}

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 헤더 비밀번호 불일치 -> CoreException(UNAUTHORIZED). "
			+ "서비스 예외를 그대로 전파")
		void failWhenHeaderPasswordNotMatch() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "NewPass1234!");
			willThrow(new CoreException(ErrorType.UNAUTHORIZED)).given(userCommandService)
				.updatePassword("WrongPass1!", "testuser01", "Test1234!", "NewPass1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword("testuser01", "WrongPass1!", inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userCommandService)
				.updatePassword("WrongPass1!", "testuser01", "Test1234!", "NewPass1234!");
		}

		@Test
		@DisplayName("[UserCommandFacade.changePassword()] 현재/새 비밀번호 동일 -> CoreException(PASSWORD_SAME_AS_CURRENT). "
			+ "서비스 예외를 그대로 전파")
		void failWhenNewPasswordSameAsCurrent() {
			// Arrange
			UserChangePasswordInDto inDto = new UserChangePasswordInDto("Test1234!", "Test1234!");
			willThrow(new CoreException(ErrorType.PASSWORD_SAME_AS_CURRENT)).given(userCommandService)
				.updatePassword("Test1234!", "testuser01", "Test1234!", "Test1234!");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandFacade.changePassword("testuser01", "Test1234!", inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT.getMessage())
			);
			verify(userCommandService)
				.updatePassword("Test1234!", "testuser01", "Test1234!", "Test1234!");
		}
	}
}
