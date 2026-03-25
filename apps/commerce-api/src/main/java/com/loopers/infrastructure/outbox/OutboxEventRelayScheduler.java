package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OutboxEventRelayScheduler {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> events = outboxEventJpaRepository.findTop100ByPublishedAtIsNull();
        events.forEach(event -> {
            kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload());
            event.markPublished();
        });
    }
}
