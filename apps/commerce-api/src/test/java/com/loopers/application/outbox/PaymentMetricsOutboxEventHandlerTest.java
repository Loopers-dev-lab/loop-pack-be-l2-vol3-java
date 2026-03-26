package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.payment.event.PaymentStatusChangedEvent;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentMetricsOutboxEventHandlerTest {

    @Test
    @DisplayName("결제 성공 이벤트는 주문 아이템 수만큼 판매 메트릭 outbox row를 생성한다")
    void handle_paymentSucceeded_savesOutboxPerOrderItem() {
        OrderApplicationService orderApplicationService = mock(OrderApplicationService.class);
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        PaymentMetricsOutboxEventHandler handler = new PaymentMetricsOutboxEventHandler(
                orderApplicationService,
                outboxEventRepository,
                new ObjectMapper().findAndRegisterModules()
        );
        ReflectionTestUtils.setField(handler, "productMetricsTopic", "commerce.product.metrics.v1");

        UUID orderId = UUID.randomUUID();
        Order order = new Order(
                orderId,
                "member-1",
                "ORDER-1",
                ZonedDateTime.now(),
                OrderStatus.ORDERED,
                10000,
                null,
                0,
                List.of(
                        new OrderItem(UUID.randomUUID(), 2, "A", 1000, "B"),
                        new OrderItem(UUID.randomUUID(), 1, "C", 2000, "D")
                ),
                null,
                null
        );
        when(orderApplicationService.getByIdForSystem(orderId)).thenReturn(order);

        handler.handle(new PaymentStatusChangedEvent(
                "member-1",
                orderId,
                PaymentStatus.REQUESTED,
                PaymentStatus.SUCCEEDED,
                Instant.now()
        ));

        verify(outboxEventRepository, times(2)).save(any());
    }
}
