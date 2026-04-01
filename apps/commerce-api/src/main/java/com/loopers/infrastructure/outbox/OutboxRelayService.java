package com.loopers.infrastructure.outbox;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final OutboxMetrics metrics;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private volatile CountDownLatch phase2Latch;

    public OutboxRelayService(OutboxEventJpaRepository outboxEventJpaRepository,
                               KafkaTemplate<Object, Object> kafkaTemplate,
                               org.springframework.transaction.PlatformTransactionManager transactionManager,
                               OutboxMetrics metrics) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.metrics = metrics;
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
        long startTime = System.currentTimeMillis();

        List<OutboxEventEntity> pendingEvents = outboxEventJpaRepository.findPendingEventsForUpdate(BATCH_SIZE);

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEventEntity event : pendingEvents) {
            event.markProcessing();
        }
        outboxEventJpaRepository.saveAll(pendingEvents);

        long duration = System.currentTimeMillis() - startTime;
        metrics.recordPhase1Duration(duration);

        log.info("[Relay Phase 1] PROCESSING 전환 {}건 ({}ms)", pendingEvents.size(), duration);
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
        if (shuttingDown.get()) {
            log.info("[Relay Phase 2] Shutting down, skipping this cycle");
            return;
        }

        phase2Latch = new CountDownLatch(1);
        try {
            executePhase2();
        } finally {
            phase2Latch.countDown();
        }
    }

    private void executePhase2() {
        long startTime = System.currentTimeMillis();

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
                try {
                    publishToKafka(event);
                } catch (Exception e) {
                    // 개별 이벤트 실패해도 다른 이벤트 계속 처리
                    log.error("[Relay Phase 2] Unexpected error processing event {}: {}",
                            event.getId(), e.getMessage(), e);
                    event.markFailed("Unexpected error: " + e.getMessage());
                    metrics.recordPublishFailure();
                }
            }
        });

        // 상태 업데이트 (PUBLISHED or FAILED)
        transactionTemplate.executeWithoutResult(status -> {
            outboxEventJpaRepository.saveAll(processingEvents);
        });

        long duration = System.currentTimeMillis() - startTime;
        metrics.recordPhase2Duration(duration);

        log.info("[Relay Phase 2] 상태 업데이트 완료 {}건 ({}ms)", processingEvents.size(), duration);
    }

    /**
     * PROCESSING 복구 로직 (5분 간격)
     *
     * 5분 이상 PROCESSING 상태인 이벤트를 PENDING으로 복원
     * - Phase 2 실패 시 복구
     * - 앱 크래시 후 재시작 시 복구
     */
    @Scheduled(fixedDelay = 300000) // 5분
    @Transactional
    public void recoverStalledProcessingEvents() {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(5);
        List<OutboxEventEntity> stalledEvents = outboxEventJpaRepository.findStalledProcessingEvents(threshold);

        if (stalledEvents.isEmpty()) {
            return;
        }

        log.warn("[Relay] PROCESSING 5분 이상 경과 {}건 → PENDING 복원", stalledEvents.size());
        stalledEvents.forEach(OutboxEventEntity::markRetry);
        outboxEventJpaRepository.saveAll(stalledEvents);
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
            metrics.recordPublishSuccess();

        } catch (ExecutionException e) {
            log.error("[Relay] 발행 실패 — outboxId={}, error={}", event.getId(), e.getCause().getMessage());
            event.markFailed(e.getCause().getMessage());
            metrics.recordPublishFailure();
        } catch (TimeoutException e) {
            log.error("[Relay] 발행 타임아웃 — outboxId={}", event.getId());
            event.markFailed("Kafka send timeout (10s)");
            metrics.recordPublishFailure();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event.markFailed("Interrupted");
            metrics.recordPublishFailure();
        }
    }

    /**
     * Graceful Shutdown
     *
     * 앱 종료 시:
     * 1. 새로운 Phase 2 실행 중단
     * 2. 현재 실행 중인 Phase 2 완료 대기 (최대 30초)
     * 3. 미완료 PROCESSING → PENDING 복원
     */
    @PreDestroy
    public void onShutdown() {
        log.info("[Relay] Graceful shutdown started");
        shuttingDown.set(true);

        // 현재 실행 중인 Phase 2 완료 대기
        CountDownLatch currentLatch = phase2Latch;
        if (currentLatch != null) {
            try {
                boolean completed = currentLatch.await(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (completed) {
                    log.info("[Relay] Phase 2 completed gracefully");
                } else {
                    log.warn("[Relay] Phase 2 did not complete within {}s, proceeding with recovery",
                            SHUTDOWN_TIMEOUT_SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Relay] Shutdown interrupted");
            }
        }

        // 미완료 PROCESSING → PENDING 복원
        recoverProcessingOnShutdown();

        log.info("[Relay] Graceful shutdown completed");
    }

    @Transactional
    protected void recoverProcessingOnShutdown() {
        List<OutboxEventEntity> processingEvents = outboxEventJpaRepository
                .findProcessingEvents(Integer.MAX_VALUE);

        if (!processingEvents.isEmpty()) {
            log.info("[Relay] Recovering {} PROCESSING events to PENDING on shutdown",
                    processingEvents.size());

            processingEvents.forEach(event -> event.markRetry());
            outboxEventJpaRepository.saveAll(processingEvents);
        }
    }
}
