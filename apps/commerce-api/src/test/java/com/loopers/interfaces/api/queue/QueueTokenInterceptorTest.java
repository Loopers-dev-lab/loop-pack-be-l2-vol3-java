package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueTokenValidator;
import com.loopers.domain.queue.QueueProperties;
import com.loopers.support.error.CoreException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueTokenInterceptorTest {

    @Mock
    private QueueTokenValidator queueTokenValidator;

    @Mock
    private QueueProperties queueProperties;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private QueueTokenInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new QueueTokenInterceptor(queueTokenValidator, queueProperties);
    }

    @Nested
    @DisplayName("preHandle — 토큰 검증 분기")
    class PreHandle {

        @Test
        @DisplayName("대기열이 비활성화면 검증 없이 통과한다")
        void queueDisabled() {
            when(queueProperties.isEnabled()).thenReturn(false);

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(queueTokenValidator, never()).validateToken(1L);
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 검증 없이 통과한다")
        void noUserIdHeader() {
            when(queueProperties.isEnabled()).thenReturn(true);
            when(request.getHeader("X-User-Id")).thenReturn(null);

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("유효한 토큰이 있으면 통과한다")
        void validToken_passes() {
            when(queueProperties.isEnabled()).thenReturn(true);
            when(request.getHeader("X-User-Id")).thenReturn("42");
            when(queueTokenValidator.validateToken(42L)).thenReturn(true);

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
            verify(queueTokenValidator).validateToken(42L);
        }

        @Test
        @DisplayName("유효한 토큰이 없으면 QUEUE_TOKEN_REQUIRED 예외가 발생한다")
        void noToken_throws() {
            when(queueProperties.isEnabled()).thenReturn(true);
            when(request.getHeader("X-User-Id")).thenReturn("42");
            when(request.getRequestURI()).thenReturn("/api/v1/orders");
            when(queueTokenValidator.validateToken(42L)).thenReturn(false);

            assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                    .isInstanceOf(CoreException.class);

            verify(queueTokenValidator).validateToken(42L);
        }

        @Test
        @DisplayName("X-User-Id가 숫자가 아니면 검증 없이 통과한다")
        void invalidUserIdFormat() {
            when(queueProperties.isEnabled()).thenReturn(true);
            when(request.getHeader("X-User-Id")).thenReturn("not-a-number");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertThat(result).isTrue();
        }
    }
}
