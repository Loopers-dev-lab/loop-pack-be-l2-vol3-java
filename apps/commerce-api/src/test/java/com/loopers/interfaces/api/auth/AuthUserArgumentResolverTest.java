package com.loopers.interfaces.api.auth;

import com.loopers.domain.user.User;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthUserArgumentResolverTest {

    private AuthUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AuthUserArgumentResolver();
    }

    @DisplayName("supportsParameter 검증할 때, ")
    @Nested
    class SupportsParameter {

        @DisplayName("@AuthUser와 AuthenticatedUser 타입이면, true를 반환한다.")
        @Test
        void returnsTrue_whenAuthUserAnnotationWithAuthenticatedUserType() {
            MethodParameter parameter = mock(MethodParameter.class);
            when(parameter.hasParameterAnnotation(AuthUser.class)).thenReturn(true);
            when(parameter.getParameterType()).thenReturn((Class) AuthenticatedUser.class);

            assertThat(resolver.supportsParameter(parameter)).isTrue();
        }

        @DisplayName("@AuthUser와 User 타입이면, false를 반환한다.")
        @Test
        void returnsFalse_whenAuthUserAnnotationWithUserType() {
            MethodParameter parameter = mock(MethodParameter.class);
            when(parameter.hasParameterAnnotation(AuthUser.class)).thenReturn(true);
            when(parameter.getParameterType()).thenReturn((Class) User.class);

            assertThat(resolver.supportsParameter(parameter)).isFalse();
        }

        @DisplayName("@AuthUser 어노테이션이 없으면, false를 반환한다.")
        @Test
        void returnsFalse_whenNoAuthUserAnnotation() {
            MethodParameter parameter = mock(MethodParameter.class);
            when(parameter.hasParameterAnnotation(AuthUser.class)).thenReturn(false);
            when(parameter.getParameterType()).thenReturn((Class) AuthenticatedUser.class);

            assertThat(resolver.supportsParameter(parameter)).isFalse();
        }
    }

    @DisplayName("인증 정보를 조회할 때, ")
    @Nested
    class ResolveArgument {

        @DisplayName("request attribute에 인증 정보가 없으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenNoAuthAttribute() {
            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(httpRequest);
            when(httpRequest.getAttribute(EntryTokenInterceptor.ATTRIBUTE_AUTH_USER)).thenReturn(null);

            CoreException result = assertThrows(CoreException.class,
                () -> resolver.resolveArgument(null, null, webRequest, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("request attribute에 인증 정보가 있으면, AuthenticatedUser를 반환한다.")
        @Test
        void returnsAuthenticatedUser_whenAuthAttributeExists() {
            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            AuthenticatedUser expected = new AuthenticatedUser(1L, "user1");
            when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(httpRequest);
            when(httpRequest.getAttribute(EntryTokenInterceptor.ATTRIBUTE_AUTH_USER)).thenReturn(expected);

            AuthenticatedUser result = resolver.resolveArgument(null, null, webRequest, null);

            assertThat(result.userId()).isEqualTo(1L);
            assertThat(result.loginId()).isEqualTo("user1");
        }

        @DisplayName("NativeRequest가 null이면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenNativeRequestIsNull() {
            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(null);

            CoreException result = assertThrows(CoreException.class,
                () -> resolver.resolveArgument(null, null, webRequest, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
