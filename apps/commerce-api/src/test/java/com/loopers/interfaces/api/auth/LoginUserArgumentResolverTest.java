package com.loopers.interfaces.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

@DisplayName("LoginUserArgumentResolver 클래스")
class LoginUserArgumentResolverTest {

    private LoginUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LoginUserArgumentResolver();
    }

    @DisplayName("supportsParameter 메서드는")
    @Nested
    class SupportsParameter {

        @DisplayName("@LoginUser와 Long 타입이면 true를 반환한다")
        @Test
        void returnsTrue_whenLoginUserAnnotationWithLongType() throws NoSuchMethodException {
            // arrange
            MethodParameter parameter = new MethodParameter(TestController.class.getMethod("validMethod", Long.class), 0);

            // act
            boolean result = resolver.supportsParameter(parameter);

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("@LoginUser가 없으면 false를 반환한다")
        @Test
        void returnsFalse_whenNoAnnotation() throws NoSuchMethodException {
            // arrange
            MethodParameter parameter = new MethodParameter(TestController.class.getMethod("noAnnotationMethod", Long.class), 0);

            // act
            boolean result = resolver.supportsParameter(parameter);

            // assert
            assertThat(result).isFalse();
        }

        @DisplayName("Long 타입이 아니면 false를 반환한다")
        @Test
        void returnsFalse_whenNotLongType() throws NoSuchMethodException {
            // arrange
            MethodParameter parameter = new MethodParameter(TestController.class.getMethod("wrongTypeMethod", String.class), 0);

            // act
            boolean result = resolver.supportsParameter(parameter);

            // assert
            assertThat(result).isFalse();
        }
    }

    @DisplayName("resolveArgument 메서드는")
    @Nested
    class ResolveArgument {

        @DisplayName("request attribute에서 userId를 반환한다")
        @Test
        void returnsUserId_fromRequestAttribute() {
            // arrange
            Long expectedUserId = 123L;
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute("userId", expectedUserId);
            NativeWebRequest webRequest = new ServletWebRequest(request);

            // act
            Object result = resolver.resolveArgument(null, null, webRequest, null);

            // assert
            assertThat(result).isEqualTo(expectedUserId);
        }
    }

    // Test helper class
    @SuppressWarnings("unused")
    static class TestController {

        public void validMethod(@LoginUser Long userId) {
        }

        public void noAnnotationMethod(Long userId) {
        }

        public void wrongTypeMethod(@LoginUser String userId) {
        }
    }
}
