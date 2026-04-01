package com.loopers.infrastructure.dlq;

import com.loopers.infrastructure.event.EventHandledJpaRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * DLQ 재처리 서비스
 *
 * DLQ에 격리된 메시지를 원본 토픽으로 재발행하는 프로세스:
 *   1. event_handled에서 해당 eventId 삭제 (멱등성 레코드 초기화)
 *   2. 원본 토픽으로 메시지 재발행 (동기)
 *   3. 기존 Consumer가 정상 파이프라인으로 재처리
 *
 * 실무 패턴: Alen 멘토 — "eventId 기반 event_handled 레코드 삭제 → 원본 토픽 재발행"
 */
@Service
public class DlqReprocessingService {

    private static final Logger log = LoggerFactory.getLogger(DlqReprocessingService.class);

    private final EventHandledJpaRepository eventHandledRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public DlqReprocessingService(EventHandledJpaRepository eventHandledRepository,
                                   KafkaTemplate<Object, Object> kafkaTemplate) {
        this.eventHandledRepository = eventHandledRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 멱등성 레코드 삭제 + 원본 토픽 재발행
     */
    @Transactional
    public void reprocess(String eventId, String originalTopic, String partitionKey, String payload) {
        // 1. 멱등성 레코드 삭제
        deleteEventHandled(eventId);

        // 2. 원본 토픽으로 재발행 (동기 — 재발행 실패 시 예외)
        try {
            ProducerRecord<Object, Object> record = new ProducerRecord<>(
                    originalTopic, null, partitionKey, payload);

            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);

            log.info("[DlqReprocess] 재발행 완료 — eventId={}, topic={}, key={}",
                    eventId, originalTopic, partitionKey);

        } catch (ExecutionException | TimeoutException e) {
            log.error("[DlqReprocess] 재발행 실패 — eventId={}, topic={}, error={}",
                    eventId, originalTopic, e.getMessage());
            throw new RuntimeException("DLQ 재발행 실패: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("DLQ 재발행 중단", e);
        }
    }

    /**
     * 멱등성 레코드만 삭제 (재발행은 별도)
     *
     * @return true = 삭제됨, false = 레코드 없었음
     */
    @Transactional
    public boolean deleteEventHandled(String eventId) {
        return eventHandledRepository.deleteByEventId(eventId) > 0;
    }
}
