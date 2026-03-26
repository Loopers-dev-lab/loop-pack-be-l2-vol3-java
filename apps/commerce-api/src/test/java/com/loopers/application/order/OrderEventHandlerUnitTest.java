package com.loopers.application.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.order.event.OrderCreatedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventHandlerUnitTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderEventHandler handler;

    @DisplayName("BEFORE_COMMIT 핸들러 (Outbox 저장)")
    @Nested
    class BeforeCommitHandler {

        @DisplayName("OrderCreatedEvent 수신 시 Outbox에 ORDER_CREATED 이벤트가 저장된다")
        @Test
        void saveToOutboxSuccess() throws Exception {
            // given
            OrderCreatedEvent event = new OrderCreatedEvent(100L, 1L, 258000, 2);
            when(objectMapper.writeValueAsString(event))
                    .thenReturn("{\"orderId\":100,\"memberId\":1,\"totalAmount\":258000,\"itemCount\":2}");

            // when
            handler.saveToOutbox(event);

            // then
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertThat(saved.getAggregateType()).isEqualTo("ORDER");
            assertThat(saved.getAggregateId()).isEqualTo(100L);
            assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
            assertThat(saved.isPublished()).isFalse();
            assertThat(saved.getEventId()).isNotNull();
        }
    }

    @DisplayName("AFTER_COMMIT 핸들러 (비동기 로깅)")
    @Nested
    class AfterCommitHandler {

        @DisplayName("주문 생성 이벤트 로깅이 예외 없이 실행된다")
        @Test
        void handleOrderCreatedLogsSuccessfully() {
            // given
            OrderCreatedEvent event = new OrderCreatedEvent(100L, 1L, 258000, 2);

            // when & then - 예외 없이 실행됨
            handler.handleOrderCreated(event);
        }
    }
}
