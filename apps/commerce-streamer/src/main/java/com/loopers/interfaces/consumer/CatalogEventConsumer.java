package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.event.model.EventHandleStatus;
import com.loopers.domain.metrics.service.MetricsService;
import com.loopers.infrastructure.event.entity.EventHandledEntity;
import com.loopers.infrastructure.event.repository.EventHandledJpaRepository;
import com.loopers.interfaces.consumer.dto.CatalogEventMessage;
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
public class CatalogEventConsumer {

    private final MetricsService metricsService;
    private final EventHandledJpaRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(topics = "catalog-events", containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                transactionTemplate.executeWithoutResult(status -> processRecord(record));
            } catch (Exception e) {
                log.error("catalog-events 처리 실패 - record: {}", record, e);
            }
        }
        ack.acknowledge();
    }

    private void processRecord(ConsumerRecord<Object, Object> record) {
        CatalogEventMessage event = KafkaMessageUtil.readValue(objectMapper, record.value(), CatalogEventMessage.class);

        if (eventHandledRepository.existsById(event.eventId())) {
            return;
        }

        switch (event.eventType()) {
            case PRODUCT_VIEWED -> metricsService.incrementViewCount(event.productId(), event.version());
            case FAVORITE_ADDED -> metricsService.incrementLikeCount(event.productId(), event.version());
            case FAVORITE_REMOVED -> metricsService.decrementLikeCount(event.productId(), event.version());
            default -> {}
        }

        eventHandledRepository.save(EventHandledEntity.of(event.eventId(), EventHandleStatus.SUCCESS));

        log.info("catalog-events 처리 완료 - eventType: {}, productId: {}", event.eventType(), event.productId());
    }

}
