package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OrderPaymentOutboxServiceTest {

    @Test
    @DisplayName("주문 생성 이벤트를 outbox에 저장한다")
    void saveOrderCreated() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OrderPaymentOutboxService service = new OrderPaymentOutboxService(repository, new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(service, "orderCreatedTopic", "commerce.order.created.v1");

        service.saveOrderCreated(new OrderCreatedOutboxMessage(UUID.randomUUID(), UUID.randomUUID(), "member-1", 1000, Instant.now()));

        verify(repository, times(1)).save(any());
    }

    @Test
    @DisplayName("결제 상태 변경 이벤트를 outbox에 저장한다")
    void savePaymentStatusChanged() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OrderPaymentOutboxService service = new OrderPaymentOutboxService(repository, new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(service, "paymentStatusChangedTopic", "commerce.payment.status-changed.v1");

        service.savePaymentStatusChanged(new PaymentStatusChangedOutboxMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "member-1",
                PaymentStatus.REQUESTED,
                PaymentStatus.SUCCEEDED,
                Instant.now()
        ));

        verify(repository, times(1)).save(any());
    }
}
