package com.loopers.application.ranking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.loopers.application.ranking.RankingScoreUpdater.RANKING_TTL_SECONDS;
import static com.loopers.application.ranking.RankingScoreUpdater.zsetKey;

/**
 * 23:50 KST에 오늘 랭킹의 일부를 내일 키로 복사하여 콜드 스타트를 완화한다.
 *
 * <p>ZUNIONSTORE로 오늘 ZSET score × carryOverRate를 내일 ZSET에 시드.
 * Hash는 복사하지 않는다 — 내일 실제 이벤트가 들어오면 HINCRBY→ZADD가 덮어쓴다.</p>
 */
@Slf4j
@Component
public class RankingCarryOverScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RedisTemplate<String, String> writeTemplate;
    private final RankingProperties properties;

    public RankingCarryOverScheduler(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate,
        RankingProperties properties
    ) {
        this.writeTemplate = writeTemplate;
        this.properties = properties;
    }

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void carryOver() {
        carryOver(LocalDate.now(KST));
    }

    void carryOver(LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        String todayKey = zsetKey(today);
        String tomorrowKey = zsetKey(tomorrow);
        double rate = properties.carryOverRate();

        try {
            writeTemplate.opsForZSet().unionAndStore(
                todayKey,
                Collections.emptyList(),
                tomorrowKey,
                Aggregate.SUM,
                Weights.of(rate)
            );
            writeTemplate.expire(tomorrowKey, RANKING_TTL_SECONDS, TimeUnit.SECONDS);

            Long size = writeTemplate.opsForZSet().zCard(tomorrowKey);
            log.info("콜드 스타트 carry-over 완료: {} → {} (rate={}, members={})",
                todayKey, tomorrowKey, rate, size);
        } catch (Exception e) {
            log.error("콜드 스타트 carry-over 실패: {} → {}", todayKey, tomorrowKey, e);
        }
    }
}
