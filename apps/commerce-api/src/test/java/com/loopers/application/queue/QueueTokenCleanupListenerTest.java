package com.loopers.application.queue;

import com.loopers.domain.common.event.OrderCompletedEvent;
import com.loopers.domain.queue.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueTokenCleanupListenerTest {

    @Mock
    private QueueService queueService;

    @InjectMocks
    private QueueTokenCleanupListener listener;

    @Nested
    @DisplayName("handleOrderCompleted — 주문 완료 시 토큰 삭제")
    class HandleOrderCompleted {

        @Test
        @DisplayName("주문 완료 이벤트를 받으면 해당 유저의 토큰을 삭제한다")
        void deletesToken() {
            OrderCompletedEvent event = new OrderCompletedEvent(42L);

            listener.handleOrderCompleted(event);

            verify(queueService).deleteToken(42L);
        }

        @Test
        @DisplayName("토큰 삭제 실패해도 예외가 전파되지 않는다 (best-effort)")
        void bestEffort_deleteFailure() {
            OrderCompletedEvent event = new OrderCompletedEvent(42L);
            doThrow(new RedisConnectionFailureException("Redis down"))
                    .when(queueService).deleteToken(42L);

            assertThatCode(() -> listener.handleOrderCompleted(event))
                    .doesNotThrowAnyException();

            verify(queueService).deleteToken(42L);
        }
    }
}
