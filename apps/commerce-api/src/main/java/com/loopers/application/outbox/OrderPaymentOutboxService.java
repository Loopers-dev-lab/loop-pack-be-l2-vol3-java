package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
public class OrderPaymentOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${loopers.kafka.topic.order-created:commerce.order.created.v1}")
    private String orderCreatedTopic;

    @Value("${loopers.kafka.topic.order-cancel-requested:commerce.order.cancel-requested.v1}")
    private String orderCancelRequestedTopic;

    @Value("${loopers.kafka.topic.payment-status-changed:commerce.payment.status-changed.v1}")
    private String paymentStatusChangedTopic;

    public OrderPaymentOutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void saveOrderCreated(OrderCreatedOutboxMessage message) {
        save(message.eventId(), "ORDER_CREATED", "order", message.orderId().toString(), orderCreatedTopic, message.orderId().toString(), message);
    }

    public void saveOrderCancelRequested(OrderCancelRequestedOutboxMessage message) {
        save(message.eventId(), "ORDER_CANCEL_REQUESTED", "order", message.orderId().toString(), orderCancelRequestedTopic, message.orderId().toString(), message);
    }

    public void savePaymentStatusChanged(PaymentStatusChangedOutboxMessage message) {
        save(message.eventId(), "PAYMENT_STATUS_CHANGED", "payment", message.orderId().toString(), paymentStatusChangedTopic, message.orderId().toString(), message);
    }

    private void save(UUID eventId, String eventType, String aggregateType, String aggregateId, String topic, String partitionKey, Object payload) {
        outboxEventRepository.save(OutboxEvent.pending(
                eventId,
                eventType,
                aggregateType,
                aggregateId,
                topic,
                partitionKey,
                toJson(payload),
                ZonedDateTime.now()
        ));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "주문/결제 outbox 직렬화에 실패했습니다.");
        }
    }
}
