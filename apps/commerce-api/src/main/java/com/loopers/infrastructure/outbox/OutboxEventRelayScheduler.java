package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
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
            try {
                kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload()).get();
                event.markPublished();
            } catch (Exception e) {
                log.error("outbox 이벤트 발행 실패, 다음 주기에 재시도. topic={}, partitionKey={}", event.topic(), event.partitionKey(), e);
            }
        });
    }
}
