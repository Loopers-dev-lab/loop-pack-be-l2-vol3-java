package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.RankingService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.EventHandled;
import com.loopers.domain.ProductMetrics;
import com.loopers.infrastructure.EventHandledJpaRepository;
import com.loopers.infrastructure.ProductMetricsJpaRepository;
import com.loopers.kafka.event.CatalogEvent;
import com.loopers.kafka.topic.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    private final EventHandledJpaRepository eventHandledRepository;
    private final ProductMetricsJpaRepository productMetricsRepository;
    private final RankingService rankingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.CATALOG_EVENTS, containerFactory = KafkaConfig.BATCH_LISTENER)
    @Transactional
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                CatalogEvent event = objectMapper.readValue(record.value().toString(), CatalogEvent.class);

                // 멱등성 체크 — 이미 처리된 eventId면 skip
                if (eventHandledRepository.existsById(event.eventId())) {
                    log.debug("[CatalogEvent] duplicate skip eventId={}", event.eventId());
                    continue;
                }

                upsertMetrics(event);
                rankingService.updateRanking(event);
                eventHandledRepository.save(EventHandled.of(event.eventId()));
                log.info("[CatalogEvent] handled eventId={} type={} productId={}", event.eventId(), event.eventType(), event.productId());

            } catch (Exception e) {
                log.error("[CatalogEvent] failed record={} cause={}", record, e.getMessage());
            }
        }
        ack.acknowledge();
    }

    private void upsertMetrics(CatalogEvent event) {
        ProductMetrics metrics = productMetricsRepository.findById(event.productId())
            .orElseGet(() -> productMetricsRepository.save(ProductMetrics.init(event.productId())));

        switch (CatalogEvent.Type.valueOf(event.eventType())) {
            case LIKED -> metrics.increaseLikes(event.occurredAt());
            case UNLIKED -> metrics.decreaseLikes(event.occurredAt());
            case VIEWED -> log.debug("[CatalogEvent] VIEWED productId={} (metrics 미구현)", event.productId());
            case ORDERED -> log.debug("[CatalogEvent] ORDERED productId={} (metrics 미구현)", event.productId());
        }
    }
}
