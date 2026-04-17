package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OutboxRelayService {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxRepository outboxRepository;
    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ExecutorService outboxRelayExecutor;

    public OutboxRelayService(
        OutboxRepository outboxRepository,
        OutboxJpaRepository outboxJpaRepository,
        KafkaTemplate<Object, Object> kafkaTemplate,
        @Qualifier("outboxRelayExecutor") ExecutorService outboxRelayExecutor
    ) {
        this.outboxRepository = outboxRepository;
        this.outboxJpaRepository = outboxJpaRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxRelayExecutor = outboxRelayExecutor;
    }

    /**
     * Phase 1: PENDING 이벤트를 PROCESSING으로 전환 (FOR UPDATE SKIP LOCKED)
     * Phase 2: partitionKey별 그루핑 → 전용 스레드풀로 Kafka 발행
     */
    @Scheduled(fixedDelay = 1000)
    public void relay() {
        // Phase 1: PENDING → PROCESSING
        List<OutboxEvent> events = fetchAndMarkProcessing();
        if (events.isEmpty()) {
            return;
        }

        // Phase 2: partitionKey별 그루핑 → Kafka 발행
        Map<String, List<OutboxEvent>> grouped = events.stream()
            .collect(Collectors.groupingBy(OutboxEvent::getPartitionKey));

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<String, List<OutboxEvent>> entry : grouped.entrySet()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> publishEvents(entry.getValue()),
                outboxRelayExecutor
            );
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Outbox relay 완료: {}건 처리", events.size());
    }

    @Transactional
    public List<OutboxEvent> fetchAndMarkProcessing() {
        List<OutboxEvent> events = outboxRepository.findPendingEventsForUpdate(BATCH_SIZE);
        events.forEach(OutboxEvent::markProcessing);
        return events;
    }

    private void publishEvents(List<OutboxEvent> events) {
        for (OutboxEvent event : events) {
            try {
                ProducerRecord<Object, Object> record = new ProducerRecord<>(
                    event.getTopic(), null, event.getPartitionKey(), event.getPayload()
                );
                record.headers().add(new RecordHeader("X-Event-Type",
                    event.getEventType().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("X-Aggregate-Type",
                    event.getAggregateType().getBytes(StandardCharsets.UTF_8)));
                record.headers().add(new RecordHeader("X-Event-Id",
                    event.getEventId().getBytes(StandardCharsets.UTF_8)));

                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                event.markPublished();
                outboxJpaRepository.save(event);
            } catch (Exception e) {
                log.error("Kafka 발행 실패: eventId={}, error={}", event.getEventId(), e.getMessage());
                event.markFailed(truncate(e.getMessage(), 500));
                outboxJpaRepository.save(event);
            }
        }
    }

    /**
     * Recovery: 5분 이상 PROCESSING 상태인 이벤트 → PENDING 복구
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void recoverStalledEvents() {
        ZonedDateTime threshold = ZonedDateTime.now().minusMinutes(5);
        List<OutboxEvent> stalled = outboxRepository.findStalledProcessingEvents(threshold);
        stalled.forEach(OutboxEvent::markRetry);
        if (!stalled.isEmpty()) {
            log.warn("Stalled 이벤트 복구: {}건", stalled.size());
        }
    }

    /**
     * Recovery: 30초 이상 된 FAILED 이벤트 → PENDING 재시도 (최대 5회)
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void retryFailedEvents() {
        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(30);
        List<OutboxEvent> failed = outboxRepository.findRetryableFailedEvents(threshold, MAX_RETRY_COUNT);
        failed.forEach(OutboxEvent::markRetry);
        if (!failed.isEmpty()) {
            log.info("Failed 이벤트 재시도: {}건", failed.size());
        }
    }

    /**
     * Cleanup: 1시간 이상 된 PUBLISHED 이벤트 삭제
     */
    @Scheduled(fixedDelay = 3600000)
    @Transactional
    public void cleanupPublishedEvents() {
        ZonedDateTime threshold = ZonedDateTime.now().minusHours(1);
        outboxRepository.deletePublishedEventsBefore(threshold);
        log.info("Published 이벤트 정리 완료");
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}
