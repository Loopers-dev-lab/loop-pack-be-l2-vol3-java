package com.loopers.application.queue;

import com.loopers.domain.queue.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QueueTokenValidator 단위 테스트
 *
 * @CircuitBreaker 어노테이션은 Spring AOP Proxy가 필요하므로
 * 단위 테스트에서는 validateToken()의 정상 동작만 검증한다.
 * CB + Fallback 통합 동작은 통합 테스트에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class QueueTokenValidatorTest {

    @Mock
    private QueueService queueService;

    @InjectMocks
    private QueueTokenValidator queueTokenValidator;

    @Nested
    @DisplayName("validateToken — 토큰 검증")
    class ValidateToken {

        @Test
        @DisplayName("유효한 토큰이 있으면 true를 반환한다")
        void validToken() {
            when(queueService.hasValidToken(1L)).thenReturn(true);

            boolean result = queueTokenValidator.validateToken(1L);

            assertThat(result).isTrue();
            verify(queueService).hasValidToken(1L);
        }

        @Test
        @DisplayName("유효한 토큰이 없으면 false를 반환한다")
        void noToken() {
            when(queueService.hasValidToken(1L)).thenReturn(false);

            boolean result = queueTokenValidator.validateToken(1L);

            assertThat(result).isFalse();
            verify(queueService).hasValidToken(1L);
        }
    }
}
