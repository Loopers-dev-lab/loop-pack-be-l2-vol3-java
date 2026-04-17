package com.loopers.infrastructure.outbox;

import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static com.loopers.support.transaction.TransactionHelper.afterCommit;

/**
 * Outbox INSERT + 즉시 발행을 한 번에 처리.
 * Facade에서 이 메서드 하나만 호출하면 됨.
 *
 * 1. 같은 TX에서 Outbox INSERT (원자성)
 * 2. afterCommit에서 비동기 Kafka send (논블로킹)
 * 3. whenComplete ACK 성공 → markPublishedByEventId()로 SENT 마킹 (@Modifying + @Transactional이 자체 TX 생성)
 *    실패 시 PENDING 유지 → @Scheduled 보완(.get() 동기)이 수거
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    /**
     * Outbox에 저장하고 TX 커밋 후 즉시 비동기 발행.
     * Kafka ACK 성공 시 SENT 마킹, 실패 시 PENDING 유지 → @Scheduled 보완이 수거.
     * MANDATORY: TX 없는 컨텍스트에서 호출하면 즉시 예외 — Outbox가 비즈니스 TX 밖에서 호출되는 실수 방지.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveAndPublish(String eventType, String aggregateType, String aggregateId,
                               String topic, Object eventPayload) {
        OutboxEvent outboxEvent = outboxEventFactory.create(eventType, aggregateType, aggregateId, topic, eventPayload);
        outboxEventRepository.save(outboxEvent);

        Map<String, Object> envelope = Map.of(
                "eventId", outboxEvent.getEventId(),
                "eventType", outboxEvent.getEventType(),
                "payload", outboxEvent.getPayload()
        );
        afterCommit(() ->
                kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getAggregateId(), envelope)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.warn("즉시 발행 실패, @Scheduled가 보완 예정: eventId={}",
                                        outboxEvent.getEventId(), ex);
                            } else {
                                try {
                                    outboxEventRepository.markPublishedByEventId(outboxEvent.getEventId());
                                } catch (Exception e) {
                                    log.warn("SENT 마킹 실패, @Scheduled가 보완 예정: eventId={}",
                                            outboxEvent.getEventId(), e);
                                }
                            }
                        }));
    }
}
