package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingScoreLedger;
import com.loopers.domain.ranking.RankingScoreLedgerRepository;
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
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String LOCK_KEY = "ranking:lock:carry-over";
    private static final long LOCK_TTL_MS = 300_000L;
    private static final String RELEASE_LOCK_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final RankingScoreLedgerRepository ledgerRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final double carryOverWeight;

    public RankingCarryOverScheduler(
        RankingScoreLedgerRepository ledgerRepository,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate,
        PlatformTransactionManager transactionManager,
        @Value("${ranking.carry-over.weight}") double carryOverWeight
    ) {
        Assert.state(carryOverWeight > 0.0 && carryOverWeight <= 1.0,
            "ranking.carry-over.weight must be in (0, 1]. 현재: " + carryOverWeight);
        this.ledgerRepository = ledgerRepository;
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
            LocalDate tomorrow = today.plusDays(1);
            int count = carryOverDay(today, tomorrow);
            log.info("[RankingCarryOver] {} → {} (weight={}, rows={})",
                today, tomorrow, carryOverWeight, count);
        } catch (Exception e) {
            log.error("[RankingCarryOver] 실패: {}", e.getMessage(), e);
        } finally {
            releaseLock(lockValue);
        }
    }

    int carryOverDay(LocalDate today, LocalDate tomorrow) {
        String todayBucket = today.format(DAY_FORMAT);
        String tomorrowBucket = tomorrow.format(DAY_FORMAT);

        Integer count = transactionTemplate.execute(status -> {
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
        });
        return count == null ? 0 : count;
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
