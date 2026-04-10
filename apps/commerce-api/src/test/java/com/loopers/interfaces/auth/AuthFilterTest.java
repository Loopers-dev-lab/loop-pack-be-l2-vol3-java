package com.loopers.interfaces.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.user.AuthenticationService;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.FilterChain;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("AuthFilter 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private FilterChain filterChain;

    private AuthFilter authFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        authFilter = new AuthFilter(authenticationService, objectMapper);
    }

    @DisplayName("인증 필요 URL에 유효한 헤더가 있으면, LoginUser attribute를 설정하고 filterChain을 진행한다")
    @Test
    void setsLoginUserAttribute_whenValidHeadersOnAuthRequiredUrl() throws Exception {
        // arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("X-Loopers-LoginId", "testuser1");
        request.addHeader("X-Loopers-LoginPw", "Test1234!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        UserModel userModel = UserModel.create(
            "testuser1", "encoded", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com"
        );
        when(authenticationService.authenticate("testuser1", "Test1234!")).thenReturn(userModel);

        // act
        authFilter.doFilterInternal(request, response, filterChain);

        // assert
        LoginUser loginUser = (LoginUser) request.getAttribute("loginUser");
        assertThat(loginUser).isNotNull();
        assertThat(loginUser.loginId()).isEqualTo("testuser1");
        assertThat(loginUser.name()).isEqualTo("홍길동");
        verify(filterChain).doFilter(request, response);
    }

    @DisplayName("인증 필요 URL에 헤더가 누락되면, 401 JSON 응답을 직접 반환한다")
    @Test
    void returns401_whenHeadersMissingOnAuthRequiredUrl() throws Exception {
        // arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // act
        authFilter.doFilterInternal(request, response, filterChain);

        // assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("FAIL");
        verify(filterChain, never()).doFilter(request, response);
    }

    @DisplayName("인증 필요 URL에 인증 실패하면, 401 JSON 응답을 직접 반환한다")
    @Test
    void returns401_whenAuthenticationFailsOnAuthRequiredUrl() throws Exception {
        // arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("X-Loopers-LoginId", "testuser1");
        request.addHeader("X-Loopers-LoginPw", "Wrong1234!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authenticationService.authenticate("testuser1", "Wrong1234!"))
            .thenThrow(new CoreException(ErrorType.UNAUTHORIZED, "로그인 ID 또는 비밀번호가 일치하지 않습니다."));

        // act
        authFilter.doFilterInternal(request, response, filterChain);

        // assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("로그인 ID 또는 비밀번호가 일치하지 않습니다.");
        verify(filterChain, never()).doFilter(request, response);
    }

    @DisplayName("인증 불필요 URL이면, 헤더 없이도 filterChain을 진행한다")
    @Test
    void proceedsFilterChain_whenUrlDoesNotRequireAuth() throws Exception {
        // arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users/signup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // act
        authFilter.doFilterInternal(request, response, filterChain);

        // assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(filterChain).doFilter(request, response);
        verify(authenticationService, never()).authenticate(anyString(), anyString());
    }
}
