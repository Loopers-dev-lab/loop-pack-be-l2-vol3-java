package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<OutboxEvent> unpublished = outboxEventRepository.findUnpublished();
        for (OutboxEvent event : unpublished) {
            try {
                kafkaTemplate.send(event.getEventType(), event.getPartitionKey(), event.getPayload());
                outboxEventRepository.markPublished(event.getId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event. id={}, eventType={}", event.getId(), event.getEventType(), e);
            }
        }
    }
}
