package com.loopers.application.collector;

import com.loopers.application.ranking.RankingMetricsRedisSyncService;
import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import com.loopers.infrastructure.collector.EventHandledModel;
import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * product_metrics 갱신이 필요한 이벤트만: event_handled INSERT + 메트릭 갱신을 한 트랜잭션으로 묶는다.
 * DB 커밋 후 매트릭 기반으로 Redis ZSET 점수를 갱신한다.
 */
@Service
public class ProductEventCollectorDatabaseService {

    private static final Logger log = LoggerFactory.getLogger(ProductEventCollectorDatabaseService.class);

    private static final String PRODUCT_LIKE_CHANGED = "PRODUCT_LIKE_CHANGED";
    private static final String PRODUCT_VIEWED = "PRODUCT_VIEWED";
    private static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";

    private final EventHandledJpaRepository eventHandledJpaRepository;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final RankingMetricsRedisSyncService rankingMetricsRedisSyncService;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String dlqSuffix;
    private final Counter processedCounter;
    private final Counter duplicateCounter;

    public ProductEventCollectorDatabaseService(
            EventHandledJpaRepository eventHandledJpaRepository,
            ProductMetricsJpaRepository productMetricsJpaRepository,
            RankingMetricsRedisSyncService rankingMetricsRedisSyncService,
            KafkaTemplate<Object, Object> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${collector.product.dlq-suffix:.DLQ}") String dlqSuffix) {
        this.eventHandledJpaRepository = eventHandledJpaRepository;
        this.productMetricsJpaRepository = productMetricsJpaRepository;
        this.rankingMetricsRedisSyncService = rankingMetricsRedisSyncService;
        this.kafkaTemplate = kafkaTemplate;
        this.dlqSuffix = dlqSuffix;
        this.processedCounter = meterRegistry.counter("kafka.collector.events.processed");
        this.duplicateCounter = meterRegistry.counter("kafka.collector.events.duplicate");
    }

    /**
     * 이벤트를 처리하고 데이터베이스에 저장한다.
     *
     * @param record 컨슈머 레코드
     * @param envelope 이벤트 소싱 데이터
     * eventId: 이벤트 식별자
     * eventType: 이벤트 타입
     * occurredAt: 이벤트 발생 시각
     * partitionKey: 이벤트 파티션 키
     * data: 이벤트 데이터
     */
    @Transactional
    public void processDb(ConsumerRecord<Object, Object> record, ProductEventEnvelope envelope) {
        try {
            eventHandledJpaRepository.saveAndFlush(EventHandledModel.of(
                    envelope.eventId(),
                    record.topic(),
                    record.partition(),
                    record.offset()
            ));
        } catch (DataIntegrityViolationException duplicate) {
            duplicateCounter.increment();
            return;
        }

        String eventType = envelope.eventType();
        /** 이벤트 발생 시각 */
        Instant occurredAt = envelope.occurredAt();
        Set<Long> rankingProductIds = new LinkedHashSet<>();

        if (PRODUCT_LIKE_CHANGED.equals(eventType)) {
            Long productId = envelope.data().path("productId").asLong();
            String action = envelope.data().path("action").asText();
            long delta = "LIKED".equals(action) ? 1L : -1L;
            productMetricsJpaRepository.applyLikeDeltaIfNewer(productId, delta, occurredAt);
            rankingProductIds.add(productId);
            processedCounter.increment();
            /** 랭킹 동기화 */
            scheduleRankingSyncAfterCommit(record, envelope, rankingProductIds, occurredAt);
            return;
        }
        if (PRODUCT_VIEWED.equals(eventType)) {
            long productId = envelope.data().path("productId").asLong();
            productMetricsJpaRepository.applyViewDeltaIfNewer(productId, 1L, occurredAt);
            rankingProductIds.add(productId);
            processedCounter.increment();
            scheduleRankingSyncAfterCommit(record, envelope, rankingProductIds, occurredAt);
            return;
        }
        if (PAYMENT_COMPLETED.equals(eventType)) {
            var lines = envelope.data().path("lines");
            if (lines.isArray()) {
                for (var line : lines) {
                    long productId = line.path("productId").asLong();
                    long qty = line.path("quantity").asLong();
                    productMetricsJpaRepository.applySoldDeltaIfNewer(productId, qty, occurredAt);
                    rankingProductIds.add(productId);
                }
            }
            processedCounter.increment();
            scheduleRankingSyncAfterCommit(record, envelope, rankingProductIds, occurredAt);
        }
    }

    /**
     * 랭킹 동기화 작업을 예약한다. afterCommit 후 실행된다.
     * @param record 컨슈머 레코드
     * @param envelope 이벤트 소싱 데이터
     * @param productIds 상품 ID 목록
     * @param occurredAt 이벤트 발생 시각
     */
    private void scheduleRankingSyncAfterCommit(
            ConsumerRecord<Object, Object> record,
            ProductEventEnvelope envelope,
            Set<Long> productIds,
            Instant occurredAt) {
        if (productIds.isEmpty()) {
            return;
        }
        /** 랭킹 동기화 */
        Runnable sync = () -> {
            for (Long productId : productIds) {
                syncRankingToRedisOrDlq(record, envelope, productId, occurredAt);
            }
        };
        /** 트랜잭션 동기화 */
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sync.run();
                }
            });
        } else {
            sync.run();
        }
    }

    /**
     * 랭킹 점수를 Redis에 동기화한다.
     *
     * @param productId 상품 ID
     * @param occurredAt 이벤트 발생 시각
     */
    private void syncRankingToRedisOrDlq(
            ConsumerRecord<Object, Object> record,
            ProductEventEnvelope envelope,
            long productId,
            Instant occurredAt) {
        try {
            productMetricsJpaRepository.findById(productId).ifPresent(metrics ->
                    rankingMetricsRedisSyncService.upsertFromMetrics(metrics, occurredAt));
        } catch (RuntimeException ex) {
            publishRankingSyncFailedToDlq(record, envelope, productId, occurredAt, ex);
            log.error("ranking redis sync failed and sent to DLQ. productId={}", productId, ex);
        }
    }

    /**
     * 랭킹 동기화 실패 이벤트를 DLQ로 발행한다.
     * @param record 컨슈머 레코드
     * @param envelope 이벤트 소싱 데이터
     * @param productId 상품 ID
     * @param occurredAt 이벤트 발생 시각
     * @param ex 예외
     */
    private void publishRankingSyncFailedToDlq(
            ConsumerRecord<Object, Object> record,
            ProductEventEnvelope envelope,
            long productId,
            Instant occurredAt,
            RuntimeException ex) {
        try {
            String sourceTopic = record.topic();
            String dlqTopic = sourceTopic + dlqSuffix;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "RANKING_REDIS_SYNC_FAILED");
            payload.put("eventId", envelope.eventId());
            payload.put("eventType", envelope.eventType());
            payload.put("occurredAt", occurredAt);
            payload.put("sourceTopic", sourceTopic);
            payload.put("sourcePartition", record.partition());
            payload.put("sourceOffset", record.offset());
            payload.put("productId", productId);
            payload.put("error", ex.getClass().getSimpleName());
            payload.put("message", ex.getMessage());
            kafkaTemplate.send(dlqTopic, payload);
        } catch (RuntimeException dlqEx) {
            log.error("failed to publish ranking redis sync failure to DLQ", dlqEx);
        }
    }
}
