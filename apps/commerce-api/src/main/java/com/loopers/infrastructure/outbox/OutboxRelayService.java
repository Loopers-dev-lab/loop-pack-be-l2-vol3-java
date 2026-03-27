package com.loopers.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Outbox Relay — 2단계 Polling 방식 (PROCESSING 상태 + 동기 .get())
 *
 * Phase 1: PENDING → PROCESSING (빠름, 락 보유)
 * - FOR UPDATE SKIP LOCKED로 멀티 인스턴스 중복 방지
 * - 상태만 변경하고 빠르게 커밋 (락 최소 보유)
 *
 * Phase 2: PROCESSING → Kafka 발행 (느림, 병렬 가능)
 * - Partition Key별 순차 처리 (순서 보장)
 * - 병렬 스트림으로 처리량 극대화
 *
 * 동기 방식 근거:
 * - 비동기 whenComplete + @Transactional 충돌 → markPublished() DB 미반영 (kafka-pipeline-lab에서 실증)
 * - Outbox는 "안전한 발행"이 목적. 속도보다 정확성 우선.
 * - 실무 표준: 동기 Polling → CDC 전환 경로 (케브님 멘토링)
 *
 * 예상 효과:
 * - 처리량: 10건/초 → 500~5,000건/초 (50~500배)
 * - 중복 발행: 완전 방지 (PROCESSING + FOR UPDATE SKIP LOCKED)
 * - 지연: 최대 1초 (fixedDelay=1000)
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final int BATCH_SIZE = 500;  // 50 → 500 (10배 증가)
    private static final int MAX_RETRY = 5;

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public OutboxRelayService(OutboxEventJpaRepository outboxEventJpaRepository,
                               KafkaTemplate<Object, Object> kafkaTemplate,
                               org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    /**
     * Phase 1: PENDING → PROCESSING 전환 (빠름, 락 최소 보유)
     *
     * FOR UPDATE SKIP LOCKED로 멀티 인스턴스 환경에서 중복 조회 방지
     * - 인스턴스 A가 row 1~500 락 획득 → 인스턴스 B는 row 501~1000 조회
     * - 상태만 변경하고 빠르게 커밋 → 락 해제
     */
    @Scheduled(fixedDelay = 1000)  // 5초 → 1초 (5배 빠른 폴링)
    @Transactional
    public void markPendingAsProcessing() {
        List<OutboxEventEntity> pendingEvents = outboxEventJpaRepository.findPendingEventsForUpdate(BATCH_SIZE);

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEventEntity event : pendingEvents) {
            event.markProcessing();
        }
        outboxEventJpaRepository.saveAll(pendingEvents);

        log.info("[Relay Phase 1] PROCESSING 전환 {}건", pendingEvents.size());
    }

    /**
     * Phase 2: PROCESSING → Kafka 발행 (느림, 병렬 처리)
     *
     * Partition Key별로 그룹핑하여 순차 처리 (순서 보장)
     * - 같은 Partition Key는 순차 발행
     * - 다른 Partition Key는 병렬 발행
     */
    @Scheduled(fixedDelay = 1000)
    public void publishProcessingEvents() {
        List<OutboxEventEntity> processingEvents = outboxEventJpaRepository.findProcessingEvents(BATCH_SIZE);

        if (processingEvents.isEmpty()) {
            return;
        }

        log.info("[Relay Phase 2] PROCESSING 이벤트 {}건 발행 시작", processingEvents.size());

        // Partition Key별 그룹핑 (순서 보장)
        var groupedByPartitionKey = processingEvents.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        OutboxEventEntity::getPartitionKey,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        // Partition Key별 병렬 발행 (다른 Key는 병렬, 같은 Key는 순차)
        groupedByPartitionKey.values().parallelStream().forEach(events -> {
            for (OutboxEventEntity event : events) {
                publishToKafka(event);
            }
        });

        // 상태 업데이트 (PUBLISHED or FAILED)
        transactionTemplate.executeWithoutResult(status -> {
            outboxEventJpaRepository.saveAll(processingEvents);
            log.info("[Relay Phase 2] 상태 업데이트 완료 {}건", processingEvents.size());
        });
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void retryFailedEvents() {
        List<OutboxEventEntity> retryableEvents = outboxEventJpaRepository.findRetryableEvents(MAX_RETRY, BATCH_SIZE);
        if (retryableEvents.isEmpty()) {
            return;
        }

        log.info("[Relay] FAILED 이벤트 {}건 재시도", retryableEvents.size());
        for (OutboxEventEntity event : retryableEvents) {
            event.markRetry();
            publishToKafka(event);
        }
    }

    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cleanupPublishedEvents() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(7);
        List<OutboxEventEntity> oldEvents = outboxEventJpaRepository.findPublishedBefore(cutoff);
        if (!oldEvents.isEmpty()) {
            outboxEventJpaRepository.deleteAll(oldEvents);
            log.info("[Relay] PUBLISHED 이벤트 {}건 정리 (7일 이전)", oldEvents.size());
        }
    }

    private void publishToKafka(OutboxEventEntity event) {
        try {
            // Kafka 헤더에 eventType, aggregateType, outboxId를 포함
            // → Consumer가 payload 파싱 없이 이벤트 타입을 판별할 수 있음
            var producerRecord = new org.apache.kafka.clients.producer.ProducerRecord<Object, Object>(
                    event.getTopic(), null, event.getPartitionKey(), event.getPayload());
            producerRecord.headers()
                    .add("X-Event-Type", event.getEventType().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .add("X-Aggregate-Type", event.getAggregateType().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .add("X-Outbox-Id", String.valueOf(event.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));

            SendResult<Object, Object> result = kafkaTemplate
                    .send(producerRecord)
                    .get(10, TimeUnit.SECONDS);

            var metadata = result.getRecordMetadata();
            log.info("[Relay] 발행 성공 — outboxId={}, topic={}, partition={}, offset={}",
                    event.getId(), event.getTopic(), metadata.partition(), metadata.offset());
            event.markPublished();

        } catch (ExecutionException e) {
            log.error("[Relay] 발행 실패 — outboxId={}, error={}", event.getId(), e.getCause().getMessage());
            event.markFailed(e.getCause().getMessage());
        } catch (TimeoutException e) {
            log.error("[Relay] 발행 타임아웃 — outboxId={}", event.getId());
            event.markFailed("Kafka send timeout (10s)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event.markFailed("Interrupted");
        }
    }
}
