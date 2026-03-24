package com.loopers.infrastructure.outbox.relay;

import java.util.List;
import java.util.concurrent.CompletionException;

import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbox 이벤트를 Kafka로 중계하는 스케줄러.
 *
 * <p>1초 간격으로 발행 대기 중인 Outbox 이벤트를 polling하여 Kafka에 발행하고,
 * 발행 결과에 따라 상태를 갱신한다.</p>
 *
 * <p>발행 실패 시 재시도 가능 여부를 판별하여, 재시도 가능하면 PUBLISH_FAILED(최대 3회 후 DEAD),
 * 재시도 불가능하면 즉시 DEAD로 전이한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

    private static final long RELAY_INTERVAL_MS = 1_000;
    private static final int BATCH_SIZE = 100;

    private final OutboxEventService outboxEventService;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    /**
     * 발행 대기 중인 Outbox 이벤트를 Kafka로 발행한다.
     */
    @Scheduled(fixedDelay = RELAY_INTERVAL_MS)
    public void relay() {
        List<OutboxEvent> pendingEvents = outboxEventService.findPendingEvents(BATCH_SIZE);

        for (OutboxEvent event : pendingEvents) {
            publishEvent(event);
        }
    }

    private void publishEvent(OutboxEvent event) {
        kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        handleFailure(event, ex);
                    } else {
                        outboxEventService.publish(event.getId());
                        log.debug("[OUTBOX_RELAY] 발행 성공: topic={}, eventType={}, aggregateId={}",
                                event.getTopic(), event.getEventType(), event.getAggregateId());
                    }
                });
    }

    private void handleFailure(OutboxEvent event, Throwable e) {
        if (isNonRetryable(e)) {
            outboxEventService.dead(event.getId());
            log.error("[OUTBOX_RELAY] 재시도 불가능한 실패: topic={}, eventType={}, aggregateId={}",
                    event.getTopic(), event.getEventType(), event.getAggregateId(), e);
            return;
        }

        OutboxEvent updated = outboxEventService.publishFail(event.getId());
        if (updated.isDead()) {
            log.error("[OUTBOX_RELAY] 재시도 횟수 초과: topic={}, eventType={}, aggregateId={}",
                    event.getTopic(), event.getEventType(), event.getAggregateId(), e);
        } else {
            log.warn("[OUTBOX_RELAY] 발행 실패 (재시도 예정): topic={}, eventType={}, aggregateId={}",
                    event.getTopic(), event.getEventType(), event.getAggregateId(), e);
        }
    }

    private boolean isNonRetryable(Throwable e) {
        Throwable cause = e instanceof CompletionException ? e.getCause() : e;
        return cause instanceof SerializationException || cause instanceof RecordTooLargeException;
    }
}
