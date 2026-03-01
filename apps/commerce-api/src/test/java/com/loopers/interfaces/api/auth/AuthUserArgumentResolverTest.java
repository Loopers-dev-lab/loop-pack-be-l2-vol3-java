package com.loopers.interfaces.api.auth;

import com.loopers.application.user.UserApplicationService;
import com.loopers.domain.user.User;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthUserArgumentResolverTest {

    private UserApplicationService userApplicationService;
    private AuthUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        userApplicationService = mock(UserApplicationService.class);
        resolver = new AuthUserArgumentResolver(userApplicationService);
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

    @DisplayName("인증 헤더를 검증할 때, ")
    @Nested
    class ResolveArgument {

        @DisplayName("로그인 ID 헤더가 누락되면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginIdHeaderMissing() {
            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            when(webRequest.getHeader("X-Loopers-LoginId")).thenReturn(null);
            when(webRequest.getHeader("X-Loopers-LoginPw")).thenReturn("password");

            CoreException result = assertThrows(CoreException.class,
                () -> resolver.resolveArgument(null, null, webRequest, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("로그인 ID 헤더가 빈 문자열이면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenLoginIdHeaderBlank() {
            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            when(webRequest.getHeader("X-Loopers-LoginId")).thenReturn("  ");
            when(webRequest.getHeader("X-Loopers-LoginPw")).thenReturn("password");

            CoreException result = assertThrows(CoreException.class,
                () -> resolver.resolveArgument(null, null, webRequest, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호 헤더가 누락되면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordHeaderMissing() {
            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            when(webRequest.getHeader("X-Loopers-LoginId")).thenReturn("user1");
            when(webRequest.getHeader("X-Loopers-LoginPw")).thenReturn(null);

            CoreException result = assertThrows(CoreException.class,
                () -> resolver.resolveArgument(null, null, webRequest, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("정상 헤더이면, AuthenticatedUser를 반환한다.")
        @Test
        void returnsAuthenticatedUser_whenValidHeaders() {
            NativeWebRequest webRequest = mock(NativeWebRequest.class);
            when(webRequest.getHeader("X-Loopers-LoginId")).thenReturn("user1");
            when(webRequest.getHeader("X-Loopers-LoginPw")).thenReturn("password");

            User user = new User("user1", "encryptedPw", "홍길동", LocalDate.of(1990, 1, 1), "test@test.com");
            // User entity's ID is set by JPA, so we use reflection or trust the flow
            when(userApplicationService.authenticate("user1", "password")).thenReturn(user);

            AuthenticatedUser result = resolver.resolveArgument(null, null, webRequest, null);

            assertThat(result.loginId()).isEqualTo("user1");
        }
    }
}
