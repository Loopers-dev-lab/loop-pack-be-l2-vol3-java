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
 * Outbox Relay — Polling 방식 (동기 .get())
 *
 * 동기 방식 근거:
 * - 비동기 whenComplete + @Transactional 충돌 → markPublished() DB 미반영 (kafka-pipeline-lab에서 실증)
 * - Outbox는 "안전한 발행"이 목적. 속도보다 정확성 우선.
 * - 실무 표준: 동기 Polling → CDC 전환 경로 (케브님 멘토링)
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY = 5;

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public OutboxRelayService(OutboxEventJpaRepository outboxEventJpaRepository,
                               KafkaTemplate<Object, Object> kafkaTemplate) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEventEntity> pendingEvents = outboxEventJpaRepository.findPendingEvents(BATCH_SIZE);
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[Relay] PENDING 이벤트 {}건 발행 시작", pendingEvents.size());

        for (OutboxEventEntity event : pendingEvents) {
            publishToKafka(event);
        }
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
            SendResult<Object, Object> result = kafkaTemplate
                    .send(event.getTopic(), event.getPartitionKey(), event.getPayload())
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
