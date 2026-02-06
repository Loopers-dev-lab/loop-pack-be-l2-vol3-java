package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
	private UserQueryRepository userQueryRepository;

	private UserQueryFacade userQueryFacade;

	@BeforeEach
	void setUp() {
		userQueryFacade = new UserQueryFacade(userQueryRepository);
	}

	private User createValidUser() {
		User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
		return User.reconstruct(1L, user.getLoginId(), user.getPassword().value(), user.getName(), user.getBirthday(), user.getEmail());
	}

	@Test
	@DisplayName("[getMe()] 유효한 loginId, password -> User 반환. 비밀번호 매칭 성공")
	void getMeSuccess() {
		// Arrange
		User user = createValidUser();
		given(userQueryRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));

		// Act
		User result = userQueryFacade.getMe(VALID_LOGIN_ID, VALID_PASSWORD);

		// Assert
		assertAll(
			() -> assertThat(result).isNotNull(),
			() -> assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID),
			() -> assertThat(result.getName()).isEqualTo(VALID_NAME)
		);
	}

	@Test
	@DisplayName("[getMe()] loginId 앞뒤 공백 -> trim 적용 후 정상 조회")
	void getMeTrimsLoginId() {
		// Arrange
		User user = createValidUser();
		given(userQueryRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));

		// Act
		User result = userQueryFacade.getMe("  " + VALID_LOGIN_ID + "  ", VALID_PASSWORD);

		// Assert
		assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID);
	}

	@Test
	@DisplayName("[getMe()] loginId가 null -> UNAUTHORIZED 예외")
	void getMeFailWhenLoginIdNull() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userQueryFacade.getMe(null, VALID_PASSWORD));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
		);
	}

	@Test
	@DisplayName("[getMe()] loginId가 빈 문자열 -> UNAUTHORIZED 예외")
	void getMeFailWhenLoginIdBlank() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userQueryFacade.getMe("", VALID_PASSWORD));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
		);
	}

	@Test
	@DisplayName("[getMe()] loginId가 공백만 -> UNAUTHORIZED 예외")
	void getMeFailWhenLoginIdOnlySpaces() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userQueryFacade.getMe("   ", VALID_PASSWORD));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
		);
	}

	@Test
	@DisplayName("[getMe()] password가 null -> UNAUTHORIZED 예외")
	void getMeFailWhenPasswordNull() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userQueryFacade.getMe(VALID_LOGIN_ID, null));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
		);
	}

	@Test
	@DisplayName("[getMe()] password가 빈 문자열 -> UNAUTHORIZED 예외")
	void getMeFailWhenPasswordBlank() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> userQueryFacade.getMe(VALID_LOGIN_ID, ""));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
		);
	}

	@Test
	@DisplayName("[getMe()] 존재하지 않는 loginId -> UNAUTHORIZED 예외")
	void getMeFailWhenUserNotFound() {
		// Arrange
		given(userQueryRepository.findByLoginId("nonexistent")).willReturn(Optional.empty());

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
	@DisplayName("[getMe()] 비밀번호 불일치 -> UNAUTHORIZED 예외. User.authenticate() 위임")
	void getMeFailWhenPasswordNotMatch() {
		// Arrange
		User user = createValidUser();
		given(userQueryRepository.findByLoginId(VALID_LOGIN_ID)).willReturn(Optional.of(user));

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
