package com.loopers.application.queue;

import com.loopers.domain.order.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QueueEventListenerTest {

    QueueFacade queueFacade = mock(QueueFacade.class);
    QueueEventListener listener = new QueueEventListener(queueFacade);

    @DisplayName("주문 생성 이벤트 수신 시, ")
    @Nested
    class Handle {

        @DisplayName("해당 유저의 토큰 삭제를 위임한다.")
        @Test
        void removesToken_whenOrderCreatedEventReceived() {
            // arrange
            OrderEvent.Created event = new OrderEvent.Created(1L, "20260402-ABCDEF", 150000L, null, List.of());

            // act
            listener.handle(event);

            // assert
            verify(queueFacade).removeToken(1L);
        }

        @DisplayName("토큰 삭제 중 예외가 발생해도 예외를 전파하지 않는다.")
        @Test
        void doesNotThrow_whenTokenRemovalFails() {
            // arrange
            OrderEvent.Created event = new OrderEvent.Created(1L, "20260402-ABCDEF", 150000L, null, List.of());
            doThrow(new RuntimeException("fail")).when(queueFacade).removeToken(1L);

            // act & assert
            assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
            verify(queueFacade).removeToken(1L);
        }
    }
}
