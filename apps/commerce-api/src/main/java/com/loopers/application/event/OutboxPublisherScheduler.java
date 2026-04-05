package com.loopers.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.OutboxEventModel;
import com.loopers.infrastructure.event.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${commerce.outbox.publish.fixed-delay-ms:1000}")
    @SchedulerLock(name = "publishPendingEvents", lockAtLeastFor = "PT1S")
    public void publishPendingEvents() {
        List<OutboxEventModel> events = outboxEventJpaRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
        for (OutboxEventModel event : events) {
            try {
                Object payload = objectMapper.readValue(event.getPayload(), Object.class);
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), payload).get();
                markPublished(event.getEventId());
            } catch (Exception e) {
                log.warn("Outbox publish failed. eventId={}", event.getEventId(), e);
                break;
            }
        }
    }

    @Transactional
    public void markPublished(String eventId) {
        outboxEventJpaRepository.findByEventId(eventId).ifPresent(outbox -> {
            outbox.markPublished();
        });
    }
}
