package com.loopers.infrastructure.outbox.relay;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "loopers.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxEventPoller outboxEventPoller;
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelayString = "${loopers.outbox.relay.fixed-delay-ms:1000}")
    public void relay() {
        List<OutboxEvent> candidates = outboxEventPoller.poll(200);
        for (OutboxEvent event : candidates) {
            try {
                kafkaTemplate.send(event.topic(), event.partitionKey(), event.payloadJson()).get();
                outboxEventRepository.markPublished(event.id(), ZonedDateTime.now());
            } catch (Exception e) {
                outboxEventRepository.markFailed(
                        event.id(),
                        e.getMessage(),
                        ZonedDateTime.now().plusSeconds(3)
                );
                log.warn("outbox_relay_failed eventId={} topic={} partitionKey={}", event.eventId(), event.topic(), event.partitionKey(), e);
            }
        }
    }
}
