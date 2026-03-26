package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderCancelRequestedConsumerService;
import com.loopers.application.order.OrderCreatedConsumerService;
import com.loopers.application.payment.PaymentStatusChangedConsumerService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.contract.kafka.OrderCancelRequestedOutboxMessage;
import com.loopers.contract.kafka.OrderCreatedOutboxMessage;
import com.loopers.contract.kafka.PaymentStatusChangedOutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentKafkaConsumer {

    private static final String ORDER_CREATED_GROUP = "commerce-collector-order-created";
    private static final String ORDER_CANCEL_REQUESTED_GROUP = "commerce-collector-order-cancel-requested";
    private static final String PAYMENT_STATUS_CHANGED_GROUP = "commerce-collector-payment-status-changed";

    private final ObjectMapper objectMapper;
    private final OrderCreatedConsumerService orderCreatedConsumerService;
    private final OrderCancelRequestedConsumerService orderCancelRequestedConsumerService;
    private final PaymentStatusChangedConsumerService paymentStatusChangedConsumerService;

    @KafkaListener(topics = {"${loopers.kafka.topic.order-created:commerce.order.created.v1}"}, containerFactory = KafkaConfig.BATCH_LISTENER)
    public void orderCreatedListener(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> message : messages) {
            try {
                orderCreatedConsumerService.consume(ORDER_CREATED_GROUP, read(message.value(), OrderCreatedOutboxMessage.class));
            } catch (Exception e) {
                log.warn("order_created_consume_failed topic={} partition={} offset={}", message.topic(), message.partition(), message.offset(), e);
                throw new IllegalStateException("Order created consume failed", e);
            }
        }
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = {"${loopers.kafka.topic.order-cancel-requested:commerce.order.cancel-requested.v1}"}, containerFactory = KafkaConfig.BATCH_LISTENER)
    public void orderCancelRequestedListener(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> message : messages) {
            try {
                orderCancelRequestedConsumerService.consume(ORDER_CANCEL_REQUESTED_GROUP, read(message.value(), OrderCancelRequestedOutboxMessage.class));
            } catch (Exception e) {
                log.warn("order_cancel_requested_consume_failed topic={} partition={} offset={}", message.topic(), message.partition(), message.offset(), e);
                throw new IllegalStateException("Order cancel requested consume failed", e);
            }
        }
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = {"${loopers.kafka.topic.payment-status-changed:commerce.payment.status-changed.v1}"}, containerFactory = KafkaConfig.BATCH_LISTENER)
    public void paymentStatusChangedListener(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> message : messages) {
            try {
                paymentStatusChangedConsumerService.consume(PAYMENT_STATUS_CHANGED_GROUP, read(message.value(), PaymentStatusChangedOutboxMessage.class));
            } catch (Exception e) {
                log.warn("payment_status_changed_consume_failed topic={} partition={} offset={}", message.topic(), message.partition(), message.offset(), e);
                throw new IllegalStateException("Payment status changed consume failed", e);
            }
        }
        acknowledgment.acknowledge();
    }

    private <T> T read(Object rawValue, Class<T> type) throws Exception {
        if (rawValue instanceof byte[] bytes) {
            return objectMapper.readValue(bytes, type);
        }
        if (rawValue instanceof String value) {
            return objectMapper.readValue(value, type);
        }
        return objectMapper.convertValue(rawValue, type);
    }
}
