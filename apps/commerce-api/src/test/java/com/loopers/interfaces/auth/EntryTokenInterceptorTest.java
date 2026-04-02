package com.loopers.interfaces.auth;

import com.loopers.application.queue.QueueFacade;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntryTokenInterceptorTest {

    QueueFacade queueFacade = mock(QueueFacade.class);
    EntryTokenInterceptor interceptor = new EntryTokenInterceptor(queueFacade);

    @DisplayName("preHandle() 호출 시, ")
    @Nested
    class PreHandle {

        @DisplayName("EntryTokenRequired가 없으면 그대로 통과한다.")
        @Test
        void passesThrough_whenAnnotationIsMissing() throws Exception {
            // arrange
            HandlerMethod handlerMethod = new HandlerMethod(new TestController(), TestController.class.getMethod("withoutEntryToken"));

            // act
            boolean result = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handlerMethod);

            // assert
            assertThat(result).isTrue();
            verify(queueFacade, never()).validateToken(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
        }

        @DisplayName("토큰 헤더가 없으면 403 예외가 발생한다.")
        @Test
        void throwsForbidden_whenTokenHeaderIsMissing() throws Exception {
            // arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute("authenticatedUser", new AuthenticatedUser(1L));
            HandlerMethod handlerMethod = new HandlerMethod(new TestController(), TestController.class.getMethod("withEntryToken"));

            // act & assert
            assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
        }

        @DisplayName("토큰이 유효하면 통과한다.")
        @Test
        void passesThrough_whenTokenIsValid() throws Exception {
            // arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute("authenticatedUser", new AuthenticatedUser(1L));
            request.addHeader("X-Loopers-EntryToken", "valid-token");
            when(queueFacade.validateToken(1L, "valid-token")).thenReturn(true);
            HandlerMethod handlerMethod = new HandlerMethod(new TestController(), TestController.class.getMethod("withEntryToken"));

            // act
            boolean result = interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod);

            // assert
            assertThat(result).isTrue();
            verify(queueFacade).validateToken(1L, "valid-token");
        }

        @DisplayName("토큰이 유효하지 않으면 403 예외가 발생한다.")
        @Test
        void throwsForbidden_whenTokenIsInvalid() throws Exception {
            // arrange
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute("authenticatedUser", new AuthenticatedUser(1L));
            request.addHeader("X-Loopers-EntryToken", "invalid-token");
            when(queueFacade.validateToken(1L, "invalid-token")).thenReturn(false);
            HandlerMethod handlerMethod = new HandlerMethod(new TestController(), TestController.class.getMethod("withEntryToken"));

            // act & assert
            assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
        }
    }

    static class TestController {

        public void withoutEntryToken() {
        }

        @EntryTokenRequired
        public void withEntryToken() {
        }
    }
}
