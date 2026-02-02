package com.loopers.interfaces.api.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private AuthInterceptor authInterceptor;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private User user;

    @BeforeEach
    void setUp() {
        authInterceptor = new AuthInterceptor(userRepository, passwordEncoder);
    }

    @DisplayName("preHandle을 수행할 때,")
    @Nested
    class PreHandle {

        @DisplayName("인증에 성공하면, true를 반환하고 request에 userId를 저장한다.")
        @Test
        void returnsTrue_whenAuthenticationSucceeds() throws Exception {
            // arrange
            when(request.getHeader(HEADER_LOGIN_ID)).thenReturn("testuser");
            when(request.getHeader(HEADER_LOGIN_PW)).thenReturn("Password1!");
            when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.of(user));
            when(user.getId()).thenReturn(1L);
            when(user.matchesPassword("Password1!", passwordEncoder)).thenReturn(true);

            // act
            boolean result = authInterceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isTrue();
            verify(request).setAttribute(eq("userId"), eq(1L));
        }

        @DisplayName("X-Loopers-LoginId 헤더가 없으면, 401을 반환하고 false를 반환한다.")
        @Test
        void returns401_whenLoginIdHeaderMissing() throws Exception {
            // arrange
            when(request.getHeader(HEADER_LOGIN_ID)).thenReturn(null);
            when(request.getHeader(HEADER_LOGIN_PW)).thenReturn("password123");

            // act
            boolean result = authInterceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isFalse();
            verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        }

        @DisplayName("X-Loopers-LoginPw 헤더가 없으면, 401을 반환하고 false를 반환한다.")
        @Test
        void returns401_whenLoginPwHeaderMissing() throws Exception {
            // arrange
            when(request.getHeader(HEADER_LOGIN_ID)).thenReturn("testuser");
            when(request.getHeader(HEADER_LOGIN_PW)).thenReturn(null);

            // act
            boolean result = authInterceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isFalse();
            verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        }

        @DisplayName("로그인 ID가 존재하지 않으면, 401을 반환하고 false를 반환한다.")
        @Test
        void returns401_whenLoginIdNotFound() throws Exception {
            // arrange
            when(request.getHeader(HEADER_LOGIN_ID)).thenReturn("nonexistent");
            when(request.getHeader(HEADER_LOGIN_PW)).thenReturn("Password1!");
            when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.empty());

            // act
            boolean result = authInterceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isFalse();
            verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        }

        @DisplayName("비밀번호가 일치하지 않으면, 401을 반환하고 false를 반환한다.")
        @Test
        void returns401_whenPasswordNotMatches() throws Exception {
            // arrange
            when(request.getHeader(HEADER_LOGIN_ID)).thenReturn("testuser");
            when(request.getHeader(HEADER_LOGIN_PW)).thenReturn("wrongpassword");
            when(userRepository.findByLoginId(any(LoginId.class))).thenReturn(Optional.of(user));
            when(user.matchesPassword("wrongpassword", passwordEncoder)).thenReturn(false);

            // act
            boolean result = authInterceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isFalse();
            verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        }
    }
}