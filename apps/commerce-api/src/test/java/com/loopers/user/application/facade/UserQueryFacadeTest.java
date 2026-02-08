package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.out.UserMeOutDto;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryFacade 테스트")
class UserQueryFacadeTest {

	private static final String VALID_LOGIN_ID = "testuser01";
	private static final String VALID_PASSWORD = "Test1234!";
	private static final String VALID_NAME = "홍길동";
	private static final LocalDate VALID_BIRTHDAY = LocalDate.of(1990, 1, 15);
	private static final String VALID_EMAIL = "test@example.com";

	@Mock
	private UserQueryService userQueryService;

	private UserQueryFacade userQueryFacade;

	@BeforeEach
	void setUp() {
		userQueryFacade = new UserQueryFacade(userQueryService);
	}

	private User createValidUser() {
		User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
		return User.reconstruct(1L, user.getLoginId(), user.getPassword().value(), user.getName(), user.getBirthday(), user.getEmail());
	}

	@Nested
	@DisplayName("내 정보 조회 테스트")
	class GetMeTest {

		@Test
		@DisplayName("[UserQueryFacade.getMe()] 유효한 loginId, password -> UserMeOutDto 반환. 비밀번호 매칭 성공")
		void getMeSuccess() {
			// Arrange
			User user = createValidUser();
			given(userQueryService.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));

			// Act
			UserMeOutDto result = userQueryFacade.getMe(VALID_LOGIN_ID, VALID_PASSWORD);

			// Assert
			assertAll(
				() -> assertThat(result).isNotNull(),
				() -> assertThat(result.loginId()).isEqualTo(VALID_LOGIN_ID),
				() -> assertThat(result.name()).isEqualTo(VALID_NAME)
			);
		}

		@Test
		@DisplayName("[UserQueryFacade.getMe()] loginId 앞뒤 공백 -> trim 적용 후 정상 조회")
		void getMeTrimsLoginId() {
			// Arrange
			User user = createValidUser();
			given(userQueryService.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));

			// Act
			UserMeOutDto result = userQueryFacade.getMe("  " + VALID_LOGIN_ID + "  ", VALID_PASSWORD);

			// Assert
			assertThat(result.loginId()).isEqualTo(VALID_LOGIN_ID);
		}

		@Test
		@DisplayName("[UserQueryFacade.getMe()] loginId 대문자/공백 포함 -> trim + lowercase 적용 후 정상 조회")
		void getMeNormalizesLoginIdToLowerCase() {
			// Arrange
			User user = createValidUser();
			given(userQueryService.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));

			// Act
			UserMeOutDto result = userQueryFacade.getMe("  TESTUSER01  ", VALID_PASSWORD);

			// Assert
			assertThat(result.loginId()).isEqualTo(VALID_LOGIN_ID);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  ", "\t"})
		@DisplayName("[UserQueryFacade.getMe()] loginId가 null/blank -> CoreException(UNAUTHORIZED). "
			+ "인증 실패: 로그인 ID 미제공")
		void getMeFailWhenLoginIdNullOrBlank(String loginId) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryFacade.getMe(loginId, VALID_PASSWORD));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {"  ", "\t"})
		@DisplayName("[UserQueryFacade.getMe()] password가 null/blank -> CoreException(UNAUTHORIZED). "
			+ "인증 실패: 비밀번호 미제공")
		void getMeFailWhenPasswordNullOrBlank(String password) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryFacade.getMe(VALID_LOGIN_ID, password));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}

		@Test
		@DisplayName("[UserQueryFacade.getMe()] 존재하지 않는 loginId -> CoreException(UNAUTHORIZED). "
			+ "인증 실패: 사용자 미존재")
		void getMeFailWhenUserNotFound() {
			// Arrange
			given(userQueryService.findByLoginId("nonexistent")).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryFacade.getMe("nonexistent", VALID_PASSWORD));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}

		@Test
		@DisplayName("[UserQueryFacade.getMe()] 비밀번호 불일치 -> CoreException(UNAUTHORIZED). User.authenticate() 위임")
		void getMeFailWhenPasswordNotMatch() {
			// Arrange
			User user = createValidUser();
			given(userQueryService.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userQueryFacade.getMe(VALID_LOGIN_ID, "WrongPass1!"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}
	}
}
