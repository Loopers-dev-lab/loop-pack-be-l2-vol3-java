package com.loopers.user.application.service;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.repository.UserCommandRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandService 테스트")
class UserCommandServiceTest {

	@Mock
	private UserQueryService userQueryService;

	@Mock
	private UserCommandRepository userCommandRepository;

	private UserCommandService userCommandService;

	@BeforeEach
	void setUp() {
		userCommandService = new UserCommandService(userQueryService, userCommandRepository);
	}

	@Test
	@DisplayName("[UserCommandService.createUser()] 유효한 회원가입 DTO -> User 생성 후 저장. "
		+ "정규화된 loginId와 할당된 ID로 반환")
	void createUserSuccess() {
		// Arrange
		UserSignUpInDto inDto = new UserSignUpInDto(
			"  TESTUSER01  ",
			"Test1234!",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);

		given(userCommandRepository.save(any(User.class))).willAnswer(invocation -> {
			User savedUser = invocation.getArgument(0);
			return User.reconstruct(
				1L,
				savedUser.getLoginId(),
				savedUser.getPassword().value(),
				savedUser.getName(),
				savedUser.getBirthday(),
				savedUser.getEmail()
			);
		});

		// Act
		User result = userCommandService.createUser(inDto);

		// Assert
		assertAll(
			() -> assertThat(result).isNotNull(),
			() -> assertThat(result.getId()).isEqualTo(1L),
			() -> assertThat(result.getLoginId()).isEqualTo("testuser01")
		);
		verify(userCommandRepository).save(argThat(savedUser ->
			savedUser.getLoginId().equals("testuser01") &&
				savedUser.getName().equals("홍길동") &&
				savedUser.getBirthday().equals(LocalDate.of(1990, 1, 15)) &&
				savedUser.getEmail().equals("test@example.com")
		));
	}

	@Nested
	@DisplayName("유저 인증 테스트")
	class AuthenticateTest {

		@Test
		@DisplayName("[UserCommandService.authenticate()] 유효한 loginId/password -> 인증된 User 반환. "
			+ "loginId 정규화 후 조회")
		void authenticateSuccess() {
			// Arrange
			User user = User.create(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			given(userQueryService.findByLoginId("testuser01")).willReturn(Optional.of(user));

			// Act
			User authenticated = userCommandService.authenticate("  TESTUSER01  ", "Test1234!");

			// Assert
			assertThat(authenticated.getLoginId()).isEqualTo("testuser01");
			verify(userQueryService).findByLoginId("testuser01");
			verify(userCommandRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("[UserCommandService.authenticate()] 존재하지 않는 loginId -> CoreException(UNAUTHORIZED)")
		void failWhenAuthenticateUserNotFound() {
			// Arrange
			given(userQueryService.findByLoginId("nonexistent")).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.authenticate("nonexistent", "Test1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userQueryService).findByLoginId("nonexistent");
			verify(userCommandRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("[UserCommandService.authenticate()] 비밀번호 불일치 -> CoreException(UNAUTHORIZED)")
		void failWhenAuthenticatePasswordNotMatch() {
			// Arrange
			User user = User.create(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			given(userQueryService.findByLoginId("testuser01")).willReturn(Optional.of(user));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.authenticate("testuser01", "WrongPass1!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userQueryService).findByLoginId("testuser01");
			verify(userCommandRepository, never()).save(any(User.class));
		}
	}

	@Nested
	@DisplayName("비밀번호 변경 테스트")
	class UpdatePasswordTest {

		@Test
		@DisplayName("[UserCommandService.updatePassword()] 유효한 입력 -> loginId 정규화 후 조회/인증/비밀번호 변경/저장")
		void updatePasswordSuccess() {
			// Arrange
			User user = User.create(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			given(userQueryService.findByLoginId("testuser01")).willReturn(Optional.of(user));
			given(userCommandRepository.save(any(User.class))).willReturn(user);

			// Act
			assertDoesNotThrow(() ->
				userCommandService.updatePassword("Test1234!", "  TESTUSER01  ", "Test1234!", "NewPass1234!"));

			// Assert
			verify(userQueryService).findByLoginId("testuser01");
			verify(userCommandRepository).save(user);
			assertDoesNotThrow(() -> user.authenticate("NewPass1234!"));
		}

		@Test
		@DisplayName("[UserCommandService.updatePassword()] 존재하지 않는 loginId -> CoreException(UNAUTHORIZED)")
		void failWhenUserNotFound() {
			// Arrange
			given(userQueryService.findByLoginId("nonexistent")).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.updatePassword("Test1234!", "nonexistent", "Test1234!", "NewPass1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userQueryService).findByLoginId("nonexistent");
			verify(userCommandRepository, never()).save(any(User.class));
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  ", "\t"})
		@DisplayName("[UserCommandService.updatePassword()] rawLoginId null/blank -> CoreException(UNAUTHORIZED)")
		void failWhenRawLoginIdIsNullOrBlank(String rawLoginId) {
			// Arrange
			given(userQueryService.findByLoginId(null)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.updatePassword("Test1234!", rawLoginId, "Test1234!", "NewPass1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userQueryService).findByLoginId(null);
			verify(userCommandRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("[UserCommandService.updatePassword()] 헤더 비밀번호 불일치 -> CoreException(UNAUTHORIZED)")
		void failWhenHeaderPasswordNotMatch() {
			// Arrange
			User user = User.create(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			given(userQueryService.findByLoginId("testuser01")).willReturn(Optional.of(user));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.updatePassword("WrongPass1!", "testuser01", "Test1234!", "NewPass1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userQueryService).findByLoginId("testuser01");
			verify(userCommandRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("[UserCommandService.updatePassword()] currentPassword 불일치 -> CoreException(UNAUTHORIZED)")
		void failWhenCurrentPasswordNotMatch() {
			// Arrange
			User user = User.create(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			given(userQueryService.findByLoginId("testuser01")).willReturn(Optional.of(user));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.updatePassword("Test1234!", "testuser01", "WrongCurrent1!", "NewPass1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
			verify(userQueryService).findByLoginId("testuser01");
			verify(userCommandRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("[UserCommandService.updatePassword()] 새 비밀번호가 현재 비밀번호와 동일 -> CoreException(PASSWORD_SAME_AS_CURRENT)")
		void failWhenNewPasswordSameAsCurrent() {
			// Arrange
			User user = User.create(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);
			given(userQueryService.findByLoginId("testuser01")).willReturn(Optional.of(user));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userCommandService.updatePassword("Test1234!", "testuser01", "Test1234!", "Test1234!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_SAME_AS_CURRENT.getMessage())
			);
			verify(userQueryService).findByLoginId("testuser01");
			verify(userCommandRepository, never()).save(any(User.class));
		}
	}
}
