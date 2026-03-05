package com.loopers.interfaces.interceptor;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.resolver.LoginUserArgumentResolver;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String VALID_LOGIN_ID = "namjin123";
    private static final String VALID_PASSWORD = "qwer@1234";
    private static final UserInfo VALID_USER_INFO = new UserInfo(1L, VALID_LOGIN_ID, "테스트", LocalDate.of(2000, 1, 1), "test@test.com");

    @Mock
    private UserFacade userFacade;

    @InjectMocks
    private AuthInterceptor authInterceptor;

    @DisplayName("인증 인터셉터에서, ")
    @Nested
    class PreHandle {

        @DisplayName("유효한 인증 헤더로 요청하면, 인증에 성공하고 true를 반환한다.")
        @Test
        void returnsTrue_whenCredentialsAreValid() {
            // arrange
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);

            when(request.getHeader(HEADER_LOGIN_ID)).thenReturn(VALID_LOGIN_ID);
            when(request.getHeader(HEADER_LOGIN_PW)).thenReturn(VALID_PASSWORD);
            when(userFacade.authenticate(VALID_LOGIN_ID, VALID_PASSWORD)).thenReturn(VALID_USER_INFO);

            // act
            boolean result = authInterceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isTrue();
            verify(userFacade).authenticate(VALID_LOGIN_ID, VALID_PASSWORD);
            verify(request).setAttribute(LoginUserArgumentResolver.ATTR_LOGIN_USER, VALID_USER_INFO);
        }

        @DisplayName("인증 헤더가 누락되면, 예외가 발생한다.")
        @Test
        void throwsException_whenHeadersMissing() {
            // arrange
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);

            when(request.getRequestURI()).thenReturn("/api/v1/users/me");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");

            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> {
                authInterceptor.preHandle(request, response, new Object());
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
            verify(userFacade, never()).authenticate(any(), any());
        }

        @DisplayName("인증에 실패하면, 예외가 전파된다.")
        @Test
        void throwsException_whenAuthenticationFails() {
            // arrange
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);

            when(request.getHeader(HEADER_LOGIN_ID)).thenReturn(VALID_LOGIN_ID);
            when(request.getHeader(HEADER_LOGIN_PW)).thenReturn("wrongPassword");
            when(request.getRequestURI()).thenReturn("/api/v1/users/me");

            when(userFacade.authenticate(VALID_LOGIN_ID, "wrongPassword"))
                    .thenThrow(new CoreException(ErrorType.UNAUTHORIZED, "회원 정보가 올바르지 않습니다."));

            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> {
                authInterceptor.preHandle(request, response, new Object());
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("빈 문자열 인증 헤더로 요청하면, 예외가 발생한다.")
        @Test
        void throwsException_whenHeadersAreBlank() {
            // arrange
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);

            when(request.getHeader(HEADER_LOGIN_ID)).thenReturn("   ");
            when(request.getHeader(HEADER_LOGIN_PW)).thenReturn("");

            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> {
                authInterceptor.preHandle(request, response, new Object());
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
            verify(userFacade, never()).authenticate(any(), any());
        }
    }
}
