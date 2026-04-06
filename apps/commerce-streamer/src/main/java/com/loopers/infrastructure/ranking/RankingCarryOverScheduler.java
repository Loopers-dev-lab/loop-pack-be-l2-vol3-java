package com.loopers.infrastructure.ranking;

import com.loopers.support.redis.RankingKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@ConditionalOnProperty(name = "ranking.carry-over.enabled", havingValue = "true")
@EnableScheduling
@Component
public class RankingCarryOverScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String LOCK_KEY = "ranking:lock:carry-over";
    private static final long LOCK_TTL_MS = 300_000;
    private static final String RELEASE_LOCK_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final RedisTemplate<String, String> redisTemplate;
    private final double carryOverWeight;
    private final long dayTtlSeconds;

    public RankingCarryOverScheduler(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate,
        @Value("${ranking.carry-over.weight}") double carryOverWeight,
        @Value("${ranking.ttl.day-seconds}") long dayTtlSeconds
    ) {
        Assert.state(carryOverWeight > 0.0 && carryOverWeight <= 1.0,
            "ranking.carry-over.weight must be in (0, 1]. 현재: " + carryOverWeight);
        Assert.state(dayTtlSeconds > 0,
            "ranking.ttl.day-seconds must be > 0. 현재: " + dayTtlSeconds);
        this.redisTemplate = redisTemplate;
        this.carryOverWeight = carryOverWeight;
        this.dayTtlSeconds = dayTtlSeconds;
    }

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        String lockValue = acquireLock();
        if (lockValue == null) {
            log.debug("[RankingCarryOver] 다른 인스턴스가 실행 중. skip.");
            return;
        }

        try {
            LocalDate today = LocalDate.now(KST);
            LocalDate tomorrow = today.plusDays(1);
            String todayKey = RankingKeyConstants.dayKey(today);
            String tomorrowKey = RankingKeyConstants.dayKey(tomorrow);

            byte[] destBytes = tomorrowKey.getBytes(StandardCharsets.UTF_8);
            byte[] srcBytes = todayKey.getBytes(StandardCharsets.UTF_8);

            redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.zSetCommands().zUnionStore(
                    destBytes,
                    org.springframework.data.redis.connection.zset.Aggregate.SUM,
                    org.springframework.data.redis.connection.zset.Weights.of(carryOverWeight),
                    srcBytes
                )
            );

            Boolean ttlApplied = redisTemplate.expire(tomorrowKey, dayTtlSeconds, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(ttlApplied)) {
                log.warn("[RankingCarryOver] TTL 적용 실패: key={}, ttlSeconds={}",
                    tomorrowKey, dayTtlSeconds);
            }
            log.info("[RankingCarryOver] carry-over 완료: {} → {} (weight={})",
                todayKey, tomorrowKey, carryOverWeight);
        } catch (Exception e) {
            log.error("[RankingCarryOver] carry-over 실패: {}", e.getMessage(), e);
        } finally {
            releaseLock(lockValue);
        }
    }

    private String acquireLock() {
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(LOCK_KEY, lockValue, LOCK_TTL_MS, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(acquired) ? lockValue : null;
    }

    private void releaseLock(String lockValue) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
        redisTemplate.execute(script, List.of(LOCK_KEY), lockValue);
    }
}
