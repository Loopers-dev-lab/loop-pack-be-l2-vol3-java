package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.payment.event.PaymentStatusChangedEvent;
import com.loopers.contract.kafka.ProductMetricsEventMessage;
import com.loopers.domain.order.Order;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;
import java.util.UUID;

@Component
public class PaymentMetricsOutboxEventHandler {

    private final OrderApplicationService orderApplicationService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${loopers.kafka.topic.product-metrics:commerce.product.metrics.v1}")
    private String productMetricsTopic;

    public PaymentMetricsOutboxEventHandler(
            OrderApplicationService orderApplicationService,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.orderApplicationService = orderApplicationService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(PaymentStatusChangedEvent event) {
        long salesDelta = resolveSalesDelta(event);
        if (salesDelta == 0) {
            return;
        }

        Order order = orderApplicationService.getByIdForSystem(event.orderId());
        for (var item : order.items()) {
            ProductMetricsEventMessage message = new ProductMetricsEventMessage(
                    UUID.randomUUID(),
                    event.afterStatus().name(),
                    item.productId().toString(),
                    0,
                    salesDelta * item.quantity(),
                    0,
                    event.changedAt().toEpochMilli(),
                    event.changedAt()
            );
            outboxEventRepository.save(OutboxEvent.pending(
                    message.eventId(),
                    event.afterStatus().name(),
                    "order_payment_metrics",
                    item.productId().toString(),
                    productMetricsTopic,
                    item.productId().toString(),
                    toJson(message),
                    ZonedDateTime.now()
            ));
        }
    }

    private long resolveSalesDelta(PaymentStatusChangedEvent event) {
        if (event.afterStatus() == PaymentStatus.SUCCEEDED) {
            return 1L;
        }
        if (event.afterStatus() == PaymentStatus.CANCELLED && event.beforeStatus() != PaymentStatus.CANCELLED) {
            return -1L;
        }
        return 0L;
    }

    private String toJson(ProductMetricsEventMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 메트릭 outbox 직렬화에 실패했습니다.");
        }
    }
}
