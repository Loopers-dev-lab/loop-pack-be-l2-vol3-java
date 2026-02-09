package com.loopers.interfaces.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private AuthInterceptor authInterceptor;

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        authInterceptor = new AuthInterceptor(userService);
    }

    @DisplayName("preHandle을 수행할 때,")
    @Nested
    class PreHandle {

        @DisplayName("인증에 성공하면, true를 반환하고 request에 userId를 저장한다.")
        @Test
        void returnsTrue_whenAuthenticationSucceeds() throws Exception {
            // arrange
            given(request.getHeader(HEADER_LOGIN_ID)).willReturn("testuser");
            given(request.getHeader(HEADER_LOGIN_PW)).willReturn("Password1!");
            given(userService.login("testuser", "Password1!")).willReturn(1L);

            // act
            boolean result = authInterceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isTrue();
            verify(request).setAttribute("userId", 1L);
        }

        @DisplayName("인증에 실패하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorizedException_whenAuthenticationFails() {
            // arrange
            given(request.getHeader(HEADER_LOGIN_ID)).willReturn("testuser");
            given(request.getHeader(HEADER_LOGIN_PW)).willReturn("wrongpassword");
            given(userService.login("testuser", "wrongpassword"))
                    .willThrow(new CoreException(ErrorType.UNAUTHORIZED));

            // act & assert
            assertThatThrownBy(() -> authInterceptor.preHandle(request, response, new Object()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("인증 헤더가 누락되거나 빈 문자열이면, UNAUTHORIZED 예외가 발생한다.")
        @ParameterizedTest(name = "{2}")
        @MethodSource("invalidHeaderCases")
        void throwsUnauthorizedException_whenHeaderIsMissingOrEmpty(String loginId, String loginPw, String description) {
            // arrange
            given(request.getHeader(HEADER_LOGIN_ID)).willReturn(loginId);
            given(request.getHeader(HEADER_LOGIN_PW)).willReturn(loginPw);
            given(userService.login(loginId, loginPw))
                    .willThrow(new CoreException(ErrorType.UNAUTHORIZED));

            // act & assert
            assertThatThrownBy(() -> authInterceptor.preHandle(request, response, new Object()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        static Stream<Arguments> invalidHeaderCases() {
            return Stream.of(
                    Arguments.of(null, "Password1!", "LoginId 헤더 누락"),
                    Arguments.of("testuser", null, "LoginPw 헤더 누락"),
                    Arguments.of(null, null, "두 헤더 모두 누락"),
                    Arguments.of("", "Password1!", "LoginId 빈 문자열"),
                    Arguments.of("testuser", "", "LoginPw 빈 문자열")
            );
        }
    }
}
