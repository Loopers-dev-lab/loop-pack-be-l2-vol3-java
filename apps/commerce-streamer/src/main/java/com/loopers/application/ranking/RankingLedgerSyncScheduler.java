package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScoreEncoder;
import com.loopers.domain.ranking.RankingScoreLedger;
import com.loopers.domain.ranking.RankingScoreLedgerRepository;
import com.loopers.support.redis.RankingKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * dirty 상태의 ledger 행을 짧은 주기로 읽어 합성 score 로 ZADD.
 *
 * <p>at-least-once 시맨틱: Redis 쓰기는 트랜잭션 밖에서 수행하고,
 * 성공한 id만 별도 짧은 트랜잭션으로 markSynced 처리한다. 개별 Redis 실패는 dirty 로 남아 다음 사이클에 자기치유된다.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "ranking.sync.enabled", havingValue = "true", matchIfMissing = true)
public class RankingLedgerSyncScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String LOCK_KEY = "ranking:lock:sync";
    private static final long LOCK_TTL_MS = 10_000L;
    private static final String RELEASE_LOCK_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final RankingScoreLedgerRepository ledgerRepository;
    private final RankingRepository rankingRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final long dayTtlSeconds;
    private final long hourTtlSeconds;
    private final int batchSize;

    public RankingLedgerSyncScheduler(
        RankingScoreLedgerRepository ledgerRepository,
        RankingRepository rankingRepository,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate,
        PlatformTransactionManager transactionManager,
        @Value("${ranking.ttl.day-seconds}") long dayTtlSeconds,
        @Value("${ranking.ttl.hour-seconds}") long hourTtlSeconds,
        @Value("${ranking.sync.batch-size:500}") int batchSize
    ) {
        this.ledgerRepository = ledgerRepository;
        this.rankingRepository = rankingRepository;
        this.redisTemplate = redisTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dayTtlSeconds = dayTtlSeconds;
        this.hourTtlSeconds = hourTtlSeconds;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${ranking.sync.fixed-delay-ms:5000}")
    public void sync() {
        String lockValue = acquireLock();
        if (lockValue == null) {
            log.debug("[RankingSync] 다른 인스턴스 실행 중. skip.");
            return;
        }
        long startMs = System.currentTimeMillis();
        try {
            LocalDateTime now = LocalDateTime.now(KST);
            String dayBucket = RankingKeyConstants.dayBucket(now.toLocalDate());
            String hourBucket = RankingKeyConstants.hourBucket(now);

            int dayCount = flushBucket(
                RankingScoreLedger.BucketType.DAY, dayBucket,
                RankingKeyConstants.dayKey(now.toLocalDate()), dayTtlSeconds
            );
            int hourCount = flushBucket(
                RankingScoreLedger.BucketType.HOUR, hourBucket,
                RankingKeyConstants.hourKey(now), hourTtlSeconds
            );

            if (dayCount > 0 || hourCount > 0) {
                log.info("[RankingSync] flushed day={} hour={}", dayCount, hourCount);
            }
        } catch (Exception e) {
            log.error("[RankingSync] 실패: {}", e.getMessage(), e);
        } finally {
            long elapsedMs = System.currentTimeMillis() - startMs;
            if (elapsedMs > LOCK_TTL_MS * 0.8) {
                log.warn("[RankingSync] elapsed={}ms (ttl={}ms) — TTL 근접. batchSize 또는 TTL 재조정 필요.",
                    elapsedMs, LOCK_TTL_MS);
            }
            releaseLock(lockValue);
        }
    }

    private int flushBucket(
        RankingScoreLedger.BucketType bucketType, String bucketKey, String redisKey, long ttlSeconds
    ) {
        List<LedgerSnapshot> snapshots = transactionTemplate.execute(status -> {
            List<RankingScoreLedger> dirty = ledgerRepository.findDirty(bucketType, bucketKey, batchSize);
            return dirty.stream().map(LedgerSnapshot::from).toList();
        });
        if (snapshots == null || snapshots.isEmpty()) {
            return 0;
        }

        List<Long> syncedIds = new ArrayList<>(snapshots.size());
        for (LedgerSnapshot s : snapshots) {
            try {
                double composite = RankingScoreEncoder.encode(
                    s.bucketType(), s.bucketKey(), s.basePoints(), s.lastScoredAt()
                );
                rankingRepository.putScore(redisKey, s.productId(), composite, ttlSeconds);
                syncedIds.add(s.id());
            } catch (Exception e) {
                log.warn("[RankingSync] redis write failed. id={} productId={}", s.id(), s.productId(), e);
            }
        }
        if (syncedIds.isEmpty()) {
            return 0;
        }
        transactionTemplate.executeWithoutResult(
            status -> ledgerRepository.markSyncedByIds(syncedIds)
        );
        return syncedIds.size();
    }

    /**
     * 특정 날짜 ledger 전체를 강제 재동기화 (carry-over 직후 등에 사용).
     */
    public int forceSyncDay(LocalDate date) {
        String bucketKey = RankingKeyConstants.dayBucket(date);
        String redisKey = RankingKeyConstants.dayKey(date);

        List<LedgerSnapshot> snapshots = transactionTemplate.execute(status -> {
            List<RankingScoreLedger> rows =
                ledgerRepository.findAllByBucket(RankingScoreLedger.BucketType.DAY, bucketKey);
            return rows.stream().map(LedgerSnapshot::from).toList();
        });
        if (snapshots == null || snapshots.isEmpty()) {
            return 0;
        }

        List<Long> syncedIds = new ArrayList<>(snapshots.size());
        for (LedgerSnapshot s : snapshots) {
            try {
                double composite = RankingScoreEncoder.encode(
                    s.bucketType(), s.bucketKey(), s.basePoints(), s.lastScoredAt()
                );
                rankingRepository.putScore(redisKey, s.productId(), composite, dayTtlSeconds);
                syncedIds.add(s.id());
            } catch (Exception e) {
                log.warn("[RankingSync] forceSyncDay redis write failed. id={} productId={}", s.id(), s.productId(), e);
            }
        }
        if (syncedIds.isEmpty()) {
            return 0;
        }
        transactionTemplate.executeWithoutResult(
            status -> ledgerRepository.markSyncedByIds(syncedIds)
        );
        return syncedIds.size();
    }

    private String acquireLock() {
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(LOCK_KEY, lockValue, LOCK_TTL_MS, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(acquired) ? lockValue : null;
    }

    private void releaseLock(String lockValue) {
        if (lockValue == null) return;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
        redisTemplate.execute(script, List.of(LOCK_KEY), lockValue);
    }

    private record LedgerSnapshot(
        Long id,
        Long productId,
        double basePoints,
        Instant lastScoredAt,
        RankingScoreLedger.BucketType bucketType,
        String bucketKey
    ) {
        static LedgerSnapshot from(RankingScoreLedger l) {
            return new LedgerSnapshot(
                l.getId(), l.getProductId(), l.getBasePoints(),
                l.getLastScoredAt(), l.getBucketType(), l.getBucketKey()
            );
        }
    }
}
