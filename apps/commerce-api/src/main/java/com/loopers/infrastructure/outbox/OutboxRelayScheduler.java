package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
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
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents(BATCH_SIZE);
        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[Outbox] Kafka 발행 실패: eventId={}, topic={}", event.getEventId(), event.getTopic(), ex);
                        }
                    });
                event.markSent();
            } catch (Exception e) {
                log.error("[Outbox] 릴레이 처리 실패: eventId={}", event.getEventId(), e);
            }
        }

        log.debug("[Outbox] {}건 발행 완료", pendingEvents.size());
    }
}
