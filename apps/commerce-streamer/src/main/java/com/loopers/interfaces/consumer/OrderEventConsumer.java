package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.event.model.EventHandleStatus;
import com.loopers.domain.metrics.service.MetricsService;
import com.loopers.infrastructure.event.entity.EventHandledEntity;
import com.loopers.infrastructure.event.repository.EventHandledJpaRepository;
import com.loopers.interfaces.consumer.dto.OrderEventMessage;
import com.loopers.support.util.KafkaMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventConsumer {

    private final EventHandledJpaRepository eventHandledRepository;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(topics = "order-events", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                transactionTemplate.executeWithoutResult(status -> processRecord(record));
            } catch (Exception e) {
                log.error("order-events 처리 실패 - record: {}", record, e);
            }
        }
        ack.acknowledge();
    }

    private void processRecord(ConsumerRecord<Object, Object> record) {
        OrderEventMessage event = KafkaMessageUtil.readValue(objectMapper, record.value(), OrderEventMessage.class);

        if (eventHandledRepository.existsById(event.eventId())) {
            return;
        }

        switch (event.eventType()) {
            case ORDER_CREATED -> {
                log.info("주문 생성 이벤트 수신 - orderId: {}, memberId: {}", event.orderId(), event.memberId());
                if (event.orderProducts() != null) {
                    for (var product : event.orderProducts()) {
                        metricsService.incrementOrderCount(product.productId());
                    }
                }
            }
            case PAYMENT_COMPLETED ->
                log.info("결제 완료 이벤트 수신 - orderId: {}", event.orderId());
            default -> {}
        }

        eventHandledRepository.save(EventHandledEntity.of(event.eventId(), EventHandleStatus.SUCCESS));
    }
}
