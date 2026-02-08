package com.loopers.user.interfaces.controller;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.out.UserMeOutDto;
import com.loopers.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.application.facade.UserCommandFacade;
import com.loopers.user.application.facade.UserQueryFacade;
import com.loopers.user.interfaces.controller.request.UserChangePasswordRequest;
import com.loopers.user.interfaces.controller.request.UserSignUpRequest;
import com.loopers.user.interfaces.controller.response.UserMeResponse;
import com.loopers.user.interfaces.controller.response.UserSignUpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 테스트")
class UserControllerTest {

	@Mock
	private UserCommandFacade userCommandFacade;

	@Mock
	private UserQueryFacade userQueryFacade;

	private UserController userController;

	@BeforeEach
	void setUp() {
		userController = new UserController(userCommandFacade, userQueryFacade);
	}

	@Nested
	@DisplayName("POST /api/v1/users - 회원가입")
	class SignUpTest {

		@Test
		@DisplayName("[UserController.signUp()] 유효한 요청 -> 201 Created ResponseEntity 반환. 응답 body에 UserSignUpResponse 포함")
		void signUpReturnsCreatedResponse() {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"testuser01",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			UserSignUpOutDto outDto = new UserSignUpOutDto(
				1L,
				"testuser01",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			given(userCommandFacade.signUp(any())).willReturn(outDto);

			// Act
			ResponseEntity<UserSignUpResponse> response = userController.signUp(request);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().id()).isEqualTo(1L),
				() -> assertThat(response.getBody().loginId()).isEqualTo("testuser01"),
				() -> assertThat(response.getBody().name()).isEqualTo("홍길동"),
				() -> assertThat(response.getBody().birthday()).isEqualTo(LocalDate.of(1990, 1, 15)),
				() -> assertThat(response.getBody().email()).isEqualTo("test@example.com")
			);
			verify(userCommandFacade).signUp(any());
		}

		@Test
		@DisplayName("[UserController.signUp()] Facade에서 USER_ALREADY_EXISTS 예외 -> 예외 전파. "
			+ "Controller는 Facade의 CoreException을 그대로 전파")
		void signUpPropagatesUserAlreadyExistsException() {
			// Arrange
			UserSignUpRequest request = new UserSignUpRequest(
				"existinguser",
				"Test1234!",
				"홍길동",
				LocalDate.of(1990, 1, 15),
				"test@example.com"
			);

			given(userCommandFacade.signUp(any()))
				.willThrow(new CoreException(ErrorType.USER_ALREADY_EXISTS));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> userController.signUp(request));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_ALREADY_EXISTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.USER_ALREADY_EXISTS.getMessage())
			);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/users/me - 내 정보 조회")
	class GetMeTest {

		@Test
		@DisplayName("[UserController.getMe()] 유효한 인증 헤더 -> 200 OK. 응답: UserMeResponse 포함")
		void getMeReturnsOkResponse() {
			// Arrange
			UserMeOutDto outDto = new UserMeOutDto(
				"testuser01", "홍길동",
				LocalDate.of(1990, 1, 15), "test@example.com"
			);
			given(userQueryFacade.getMe("testuser01", "Test1234!")).willReturn(outDto);

			// Act
			ResponseEntity<UserMeResponse> response = userController.getMe("testuser01", "Test1234!");

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
				() -> assertThat(response.getBody()).isNotNull(),
				() -> assertThat(response.getBody().loginId()).isEqualTo("testuser01"),
				() -> assertThat(response.getBody().name()).isEqualTo("홍길*"),
				() -> assertThat(response.getBody().birthday()).isEqualTo(LocalDate.of(1990, 1, 15)),
				() -> assertThat(response.getBody().email()).isEqualTo("test@example.com")
			);
			verify(userQueryFacade).getMe("testuser01", "Test1234!");
		}

		@Test
		@DisplayName("[UserController.getMe()] 인증 실패 -> CoreException(UNAUTHORIZED) 전파")
		void getMePropagatesUnauthorizedException() {
			// Arrange
			given(userQueryFacade.getMe(null, null))
				.willThrow(new CoreException(ErrorType.UNAUTHORIZED));

			// Act & Assert
			CoreException exception = assertThrows(CoreException.class,
				() -> userController.getMe(null, null));

			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/users/me/password - 비밀번호 변경")
	class ChangePasswordTest {

		@Test
		@DisplayName("[UserController.changePassword()] 유효한 비밀번호 변경 요청 -> 200 OK. 빈 응답 본문")
		void changePasswordReturnsOkResponse() {
			// Arrange
			UserChangePasswordRequest request = new UserChangePasswordRequest("Test1234!", "NewPass1234!");

			willDoNothing().given(userCommandFacade)
				.changePassword(eq("testuser01"), eq("Test1234!"), any(UserChangePasswordInDto.class));

			// Act
			ResponseEntity<Void> response = userController.changePassword("testuser01", "Test1234!", request);

			// Assert
			assertAll(
				() -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
				() -> assertThat(response.getBody()).isNull()
			);
			verify(userCommandFacade).changePassword(eq("testuser01"), eq("Test1234!"), any(UserChangePasswordInDto.class));
		}

		@Test
		@DisplayName("[UserController.changePassword()] 인증 실패 -> CoreException(UNAUTHORIZED) 전파")
		void changePasswordPropagatesUnauthorizedException() {
			// Arrange
			UserChangePasswordRequest request = new UserChangePasswordRequest("Test1234!", "NewPass1234!");

			willThrow(new CoreException(ErrorType.UNAUTHORIZED)).given(userCommandFacade)
				.changePassword(eq(null), eq(null), any(UserChangePasswordInDto.class));

			// Act & Assert
			CoreException exception = assertThrows(CoreException.class,
				() -> userController.changePassword(null, null, request));

			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.UNAUTHORIZED.getMessage())
			);
		}
	}
}
