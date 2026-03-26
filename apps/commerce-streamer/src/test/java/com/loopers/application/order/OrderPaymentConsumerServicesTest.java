package com.loopers.application.order;

import com.loopers.contract.kafka.OrderCreatedOutboxMessage;
import com.loopers.contract.kafka.PaymentStatusChangedOutboxMessage;
import com.loopers.application.metrics.ProductMetricsAckPublisher;
import com.loopers.application.payment.PaymentStatusChangedConsumerService;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.order.OrderEventLogRepository;
import com.loopers.infrastructure.payment.PaymentEventLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPaymentConsumerServicesTest {

    @Test
    @DisplayName("주문 생성 이벤트를 처음 처리하면 ack를 발행한다")
    void orderCreated_ackPublished() {
        EventHandledRepository handledRepository = mock(EventHandledRepository.class);
        OrderEventLogRepository orderEventLogRepository = mock(OrderEventLogRepository.class);
        ProductMetricsAckPublisher ackPublisher = mock(ProductMetricsAckPublisher.class);
        OrderCreatedConsumerService service = new OrderCreatedConsumerService(handledRepository, orderEventLogRepository, ackPublisher);
        OrderCreatedOutboxMessage message = new OrderCreatedOutboxMessage(UUID.randomUUID(), UUID.randomUUID(), "member-1", 1000, Instant.now());

        when(handledRepository.markHandledIfAbsent("order-created", message.eventId())).thenReturn(true);

        service.consume("order-created", message);

        verify(orderEventLogRepository, times(1)).saveOrderCreated(message);
        verify(ackPublisher, times(1)).publish(message.eventId(), "order-created");
    }

    @Test
    @DisplayName("결제 상태 변경 이벤트가 중복이면 ack를 다시 발행하지 않는다")
    void paymentStatusChanged_duplicate_skipsAck() {
        EventHandledRepository handledRepository = mock(EventHandledRepository.class);
        PaymentEventLogRepository paymentEventLogRepository = mock(PaymentEventLogRepository.class);
        ProductMetricsAckPublisher ackPublisher = mock(ProductMetricsAckPublisher.class);
        PaymentStatusChangedConsumerService service = new PaymentStatusChangedConsumerService(handledRepository, paymentEventLogRepository, ackPublisher);
        PaymentStatusChangedOutboxMessage message = new PaymentStatusChangedOutboxMessage(
                UUID.randomUUID(), UUID.randomUUID(), "member-1", "REQUESTED", "SUCCEEDED", Instant.now());

        when(handledRepository.markHandledIfAbsent("payment-status", message.eventId())).thenReturn(false);

        service.consume("payment-status", message);

        verify(paymentEventLogRepository, never()).save(message);
        verify(ackPublisher, never()).publish(message.eventId(), "payment-status");
    }
}
