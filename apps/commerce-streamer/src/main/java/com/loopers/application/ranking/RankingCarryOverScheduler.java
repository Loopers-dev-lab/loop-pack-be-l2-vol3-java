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

import static com.loopers.application.ranking.RankingScoreUpdater.*;

/**
 * 23:50 KST에 일간 랭킹 carry-over를 수행한다.
 *
 * <p>오늘 ZSET score × carryOverRate → 내일 ZSET 시드 (콜드 스타트 완화)</p>
 *
 * <p>주간/월간 랭킹은 MV 배치(ProductRankingMvJob)가 담당하므로
 * Redis 기반 주간/월간 집계는 더 이상 수행하지 않는다.</p>
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
        double rate = properties.carryOverRate();

        RankingProperties.Experiment experiment = properties.experiment();
        if (experiment.enabled() && !experiment.variants().isEmpty()) {
            for (RankingProperties.Variant variant : experiment.variants().values()) {
                doCarryOverDaily(zsetKey(variant.zsetPrefix(), today),
                    zsetKey(variant.zsetPrefix(), tomorrow), rate);
            }
        } else {
            doCarryOverDaily(zsetKey(today), zsetKey(tomorrow), rate);
        }
    }

    private void doCarryOverDaily(String todayKey, String tomorrowKey, double rate) {
        try {
            writeTemplate.opsForZSet().unionAndStore(
                todayKey,
                Collections.emptyList(),
                tomorrowKey,
                Aggregate.SUM,
                Weights.of(rate)
            );
            trimZset(tomorrowKey);
            writeTemplate.expire(tomorrowKey, RANKING_ZSET_TTL_SECONDS, TimeUnit.SECONDS);

            Long size = writeTemplate.opsForZSet().zCard(tomorrowKey);
            log.info("콜드 스타트 carry-over 완료: {} → {} (rate={}, members={})",
                todayKey, tomorrowKey, rate, size);
        } catch (Exception e) {
            log.error("콜드 스타트 carry-over 실패: {} → {}", todayKey, tomorrowKey, e);
        }
    }

    /**
     * ZSET member 수가 cap을 초과하면 하위 score를 제거하여 상위 cap개만 유지한다.
     * carry-over에 의한 ZSET 크기 무한 누적을 방지한다.
     */
    private void trimZset(String key) {
        int cap = properties.carryOverCap();
        Long size = writeTemplate.opsForZSet().zCard(key);
        if (size != null && size > cap) {
            writeTemplate.opsForZSet().removeRange(key, 0, size - cap - 1);
            log.info("ZSET trim 완료: key={}, before={}, after={}", key, size, cap);
        }
    }

}
