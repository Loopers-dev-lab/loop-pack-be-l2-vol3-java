package com.loopers.application.order;

import com.loopers.application.metrics.ProductMetricsAckPublisher;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.order.OrderEventLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCancelRequestedConsumerServiceTest {

    @Test
    @DisplayName("주문 취소 요청 이벤트를 적재하고 ack를 발행한다")
    void consume_persistsAndPublishesAck() {
        EventHandledRepository handledRepository = mock(EventHandledRepository.class);
        OrderEventLogRepository orderEventLogRepository = mock(OrderEventLogRepository.class);
        ProductMetricsAckPublisher ackPublisher = mock(ProductMetricsAckPublisher.class);
        OrderCancelRequestedConsumerService service =
                new OrderCancelRequestedConsumerService(handledRepository, orderEventLogRepository, ackPublisher);

        OrderCancelRequestedEventMessage message = new OrderCancelRequestedEventMessage(
                UUID.randomUUID(), UUID.randomUUID(), "member-1", Instant.now()
        );

        when(handledRepository.markHandledIfAbsent("order-cancel-requested", message.eventId())).thenReturn(true);

        service.consume("order-cancel-requested", message);

        verify(orderEventLogRepository, times(1)).saveOrderCancelRequested(message);
        verify(ackPublisher, times(1)).publish(message.eventId(), "order-cancel-requested");
    }
}
