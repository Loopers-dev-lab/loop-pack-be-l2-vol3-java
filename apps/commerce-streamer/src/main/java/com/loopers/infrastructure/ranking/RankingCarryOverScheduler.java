package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingCarryOverHistory;
import com.loopers.domain.ranking.RankingCarryOverHistoryRepository;
import com.loopers.domain.ranking.RankingScoreLedger;
import com.loopers.domain.ranking.RankingScoreLedgerRepository;
import com.loopers.support.redis.RankingKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Carry-Over: 매일 23:50, 오늘 day-bucket ledger의 base points × carryOverWeight 를
 * 다음 날 day-bucket ledger에 미리 깔아둔다 (dirty=true 로 INSERT/UPDATE).
 *
 * <p>이후 다음 날의 첫 SyncScheduler 사이클에서 자연스럽게 ZADD 된다.
 *
 * <p>합성 score 인코딩 호환을 위해 Redis ZUNIONSTORE 대신 ledger upsert 방식을 사용한다.
 */
@Slf4j
@ConditionalOnProperty(name = "ranking.carry-over.enabled", havingValue = "true")
@EnableScheduling
@Component
public class RankingCarryOverScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String LOCK_KEY = "ranking:lock:carry-over";
    private static final long LOCK_TTL_MS = 300_000L;
    private static final String RELEASE_LOCK_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final RankingScoreLedgerRepository ledgerRepository;
    private final RankingCarryOverHistoryRepository historyRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final double carryOverWeight;

    public RankingCarryOverScheduler(
        RankingScoreLedgerRepository ledgerRepository,
        RankingCarryOverHistoryRepository historyRepository,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate,
        PlatformTransactionManager transactionManager,
        @Value("${ranking.carry-over.weight}") double carryOverWeight
    ) {
        Assert.state(carryOverWeight > 0.0 && carryOverWeight <= 1.0,
            "ranking.carry-over.weight must be in (0, 1]. 현재: " + carryOverWeight);
        this.ledgerRepository = ledgerRepository;
        this.historyRepository = historyRepository;
        this.redisTemplate = redisTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.carryOverWeight = carryOverWeight;
    }

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        String lockValue = acquireLock();
        if (lockValue == null) {
            log.debug("[RankingCarryOver] 다른 인스턴스 실행 중. skip.");
            return;
        }
        try {
            LocalDate today = LocalDate.now(KST);
            if (historyRepository.existsByCarryOverDate(today)) {
                log.info("[RankingCarryOver] 이미 처리됨. skip. date={}", today);
                return;
            }
            LocalDate tomorrow = today.plusDays(1);
            try {
                int count = carryOverDayWithHistory(today, tomorrow);
                log.info("[RankingCarryOver] {} → {} (weight={}, rows={})",
                    today, tomorrow, carryOverWeight, count);
            } catch (DataIntegrityViolationException race) {
                // existsByCarryOverDate 가드 통과 후 unique 충돌 — 동시 진입 race
                log.error("[RankingCarryOver] 이력 unique 충돌. 트랜잭션 롤백됨. date={}",
                    today, race);
            }
        } catch (Exception e) {
            log.error("[RankingCarryOver] 실패: {}", e.getMessage(), e);
        } finally {
            releaseLock(lockValue);
        }
    }

    /**
     * carryOverDay와 history.save를 동일 트랜잭션으로 묶는다.
     * history.save가 unique 충돌로 실패하면 전체 ledger 변경도 롤백되어 멱등성이 유지된다.
     */
    int carryOverDayWithHistory(LocalDate today, LocalDate tomorrow) {
        Integer count = transactionTemplate.execute(status -> {
            int rows = doCarryOver(today, tomorrow);
            historyRepository.save(new RankingCarryOverHistory(today, rows));
            return rows;
        });
        return count == null ? 0 : count;
    }

    private int doCarryOver(LocalDate today, LocalDate tomorrow) {
        String todayBucket = RankingKeyConstants.dayBucket(today);
        String tomorrowBucket = RankingKeyConstants.dayBucket(tomorrow);

        List<RankingScoreLedger> todayRows = ledgerRepository.findAllByBucket(
            RankingScoreLedger.BucketType.DAY, todayBucket
        );
        if (todayRows.isEmpty()) {
            return 0;
        }
        List<RankingScoreLedger> tomorrowRows = ledgerRepository.findAllByBucket(
            RankingScoreLedger.BucketType.DAY, tomorrowBucket
        );
        Map<Long, RankingScoreLedger> tomorrowMap = new HashMap<>();
        for (RankingScoreLedger r : tomorrowRows) {
            tomorrowMap.put(r.getProductId(), r);
        }

        for (RankingScoreLedger todayRow : todayRows) {
            double carryDelta = todayRow.getBasePoints() * carryOverWeight;
            if (carryDelta == 0.0) continue;
            RankingScoreLedger tomorrowRow = tomorrowMap.computeIfAbsent(
                todayRow.getProductId(),
                pid -> new RankingScoreLedger(
                    RankingScoreLedger.BucketType.DAY, tomorrowBucket, pid
                )
            );
            tomorrowRow.addScore(carryDelta);
        }
        ledgerRepository.saveAll(List.copyOf(tomorrowMap.values()));
        return todayRows.size();
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
}
