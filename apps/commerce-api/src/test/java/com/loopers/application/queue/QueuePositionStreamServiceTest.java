package com.loopers.application.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.metrics.QueueInfrastructureMetrics;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueuePositionStreamServiceTest {

    @Mock
    private QueueFacade queueFacade;

    @Mock
    private QueuePositionSseConcurrencyLimiter sseConcurrencyLimiter;

    @Mock
    private QueueInfrastructureMetrics queueInfrastructureMetrics;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QueuePositionStreamService queuePositionStreamService;

    @BeforeEach
    void setUp() {
        queuePositionStreamService =
                new QueuePositionStreamService(queueFacade, objectMapper, sseConcurrencyLimiter, queueInfrastructureMetrics);
    }

    @Test
    @DisplayName("동시 연결 한도 초과 시 예외 및 메트릭")
    void subscribe_whenLimiterRejects_shouldThrowAndRecordMetric() {
        when(sseConcurrencyLimiter.tryAcquire()).thenReturn(false);

        assertThatThrownBy(() -> queuePositionStreamService.subscribe(1L))
                .isInstanceOfSatisfying(
                        CoreException.class,
                        ex -> assertThat(ex.getErrorType()).isEqualTo(ErrorType.TOO_MANY_REQUESTS));

        verify(queueInfrastructureMetrics).recordSseConcurrencyRejected();
    }

    @Test
    @DisplayName("한도 이내면 SseEmitter 반환")
    void subscribe_whenLimiterAccepts_shouldReturnEmitter() {
        when(sseConcurrencyLimiter.tryAcquire()).thenReturn(true);

        SseEmitter emitter = queuePositionStreamService.subscribe(1L);

        assertThat(emitter).isNotNull();
        emitter.complete();
    }
}
