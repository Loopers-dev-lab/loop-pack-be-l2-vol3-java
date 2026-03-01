package com.loopers.interfaces.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";

    private AdminAuthInterceptor adminAuthInterceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        adminAuthInterceptor = new AdminAuthInterceptor();
    }

    @DisplayName("preHandle을 수행할 때,")
    @Nested
    class PreHandle {

        @DisplayName("X-Loopers-Ldap 헤더 값이 'loopers.admin'이면, true를 반환한다.")
        @Test
        void returnsTrue_whenLdapHeaderIsValid() throws Exception {
            // arrange
            given(request.getHeader(HEADER_LDAP)).willReturn("loopers.admin");

            // act
            var result = adminAuthInterceptor.preHandle(request, response, new Object());

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("유효하지 않은 X-Loopers-Ldap 헤더이면, UNAUTHORIZED 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"wrong.value"})
        void throwsUnauthorizedException_whenLdapHeaderIsInvalid(String headerValue) {
            // arrange
            given(request.getHeader(HEADER_LDAP)).willReturn(headerValue);

            // act & assert
            assertThatThrownBy(() -> adminAuthInterceptor.preHandle(request, response, new Object()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }
}
