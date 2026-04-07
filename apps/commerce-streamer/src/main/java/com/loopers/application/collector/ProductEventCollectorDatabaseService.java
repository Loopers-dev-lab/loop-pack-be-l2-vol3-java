package com.loopers.application.collector;

import com.loopers.domain.ranking.RankingMember;
import com.loopers.domain.ranking.RankingMetricCounts;
import com.loopers.domain.ranking.RankingRedisKeyResolver;
import com.loopers.domain.ranking.RankingScoreCalculator;
import com.loopers.domain.ranking.RankingTtlPolicy;
import com.loopers.domain.ranking.RankingWriteRepository;
import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import com.loopers.infrastructure.collector.EventHandledModel;
import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashSet;
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
    private final RankingScoreCalculator rankingScoreCalculator;
    private final RankingWriteRepository rankingWriteRepository;
    private final RankingRedisKeyResolver rankingRedisKeyResolver = new RankingRedisKeyResolver();
    private final RankingTtlPolicy rankingTtlPolicy = new RankingTtlPolicy();
    private final Counter processedCounter;
    private final Counter duplicateCounter;

    public ProductEventCollectorDatabaseService(
            EventHandledJpaRepository eventHandledJpaRepository,
            ProductMetricsJpaRepository productMetricsJpaRepository,
            RankingScoreCalculator rankingScoreCalculator,
            RankingWriteRepository rankingWriteRepository,
            MeterRegistry meterRegistry) {
        this.eventHandledJpaRepository = eventHandledJpaRepository;
        this.productMetricsJpaRepository = productMetricsJpaRepository;
        this.rankingScoreCalculator = rankingScoreCalculator;
        this.rankingWriteRepository = rankingWriteRepository;
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
            scheduleRankingSyncAfterCommit(rankingProductIds, occurredAt);
            return;
        }
        if (PRODUCT_VIEWED.equals(eventType)) {
            long productId = envelope.data().path("productId").asLong();
            productMetricsJpaRepository.applyViewDeltaIfNewer(productId, 1L, occurredAt);
            rankingProductIds.add(productId);
            processedCounter.increment();
            scheduleRankingSyncAfterCommit(rankingProductIds, occurredAt);
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
            scheduleRankingSyncAfterCommit(rankingProductIds, occurredAt);
        }
    }

    /**
     * 랭킹 동기화 작업을 예약한다.
     *
     * @param productIds 상품 ID 목록
     * @param occurredAt 이벤트 발생 시각
     */
    private void scheduleRankingSyncAfterCommit(Set<Long> productIds, Instant occurredAt) {
        if (productIds.isEmpty()) {
            return;
        }
        /** 랭킹 동기화 */
        Runnable sync = () -> {
            for (Long productId : productIds) {
                syncRankingToRedisOrLog(productId, occurredAt);
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
    private void syncRankingToRedisOrLog(long productId, Instant occurredAt) {
        try {
            productMetricsJpaRepository.findById(productId).ifPresent(metrics -> {
                RankingMetricCounts counts = new RankingMetricCounts(
                        metrics.getViewCount(),
                        metrics.getLikeCount(),
                        metrics.getSoldQuantity());
                double score = rankingScoreCalculator.calculate(counts);
                String key = rankingRedisKeyResolver.resolveDailyAllKey(occurredAt);
                String member = RankingMember.fromProductId(productId);
                rankingWriteRepository.upsertScore(key, member, score, rankingTtlPolicy.dailyKeyTtl());
            });
        } catch (RuntimeException ex) {
            log.error("ranking redis sync failed productId={}", productId, ex);
        }
    }
}
