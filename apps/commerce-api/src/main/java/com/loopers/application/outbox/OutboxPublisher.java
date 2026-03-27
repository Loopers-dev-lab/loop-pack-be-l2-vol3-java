package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox → Kafka 발행 스케줄러
 *
 * PENDING 상태 이벤트를 읽어 Kafka에 발행한다.
 * 발행 성공 → PUBLISHED, 실패 → FAILED (다음 주기에 재시도 가능)
 *
 * acks=all + idempotence=true 설정이므로 재시도해도 중복 발행 없음.
 * Consumer는 event_handled로 멱등 처리하므로 At Least Once 허용.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publish() {
        List<OutboxEvent> pending = outboxEventRepository.findPending();
        if (pending.isEmpty()) return;

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload());
                event.markPublished();
                log.debug("[Outbox] published eventId={} topic={}", event.getEventId(), event.getTopic());
            } catch (Exception e) {
                event.markFailed();
                log.error("[Outbox] failed eventId={} cause={}", event.getEventId(), e.getMessage());
            }
        }
    }
}
