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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.loopers.application.ranking.RankingScoreUpdater.*;

/**
 * 23:50 KST에 일간/주간/월간 랭킹을 갱신한다.
 *
 * <ol>
 *   <li><b>일간 carry-over</b>: 오늘 ZSET score × carryOverRate → 내일 ZSET 시드 (콜드 스타트 완화)</li>
 *   <li><b>주간 랭킹</b>: 최근 7일 daily ZSET ZUNIONSTORE → weekly ZSET (동일 가중치 합산)</li>
 *   <li><b>월간 랭킹</b>: 기존 monthly × decayRate + 오늘 daily → tomorrow monthly (Rolling Carry-Over)</li>
 * </ol>
 *
 * <p>per-event 추가 비용 0: 이벤트는 daily ZSET에만 쓰고, 주간/월간은 스케줄러에서 ZUNIONSTORE로 생성.</p>
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

        // 1. 일간 carry-over (콜드 스타트 완화)
        RankingProperties.Experiment experiment = properties.experiment();
        if (experiment.enabled() && !experiment.variants().isEmpty()) {
            for (RankingProperties.Variant variant : experiment.variants().values()) {
                doCarryOverDaily(zsetKey(variant.zsetPrefix(), today),
                    zsetKey(variant.zsetPrefix(), tomorrow), rate);
            }
        } else {
            doCarryOverDaily(zsetKey(today), zsetKey(tomorrow), rate);
        }

        // 2. 주간 랭킹 생성 (최근 7일 합산)
        buildWeeklyRanking(today, tomorrow);

        // 3. 월간 랭킹 생성 (Rolling Carry-Over)
        buildMonthlyRanking(today, tomorrow);
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

    /**
     * 최근 7일 daily ZSET을 동일 가중치로 합산하여 내일자 weekly ZSET을 생성한다.
     *
     * <p>ZUNIONSTORE({7일 daily}, weights=[1,1,1,1,1,1,1]) → ranking:weekly:{tomorrow}</p>
     */
    void buildWeeklyRanking(LocalDate today, LocalDate tomorrow) {
        try {
            List<String> dailyKeys = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                dailyKeys.add(zsetKey(today.minusDays(i)));
            }

            String destKey = weeklyKey(tomorrow);
            String firstKey = dailyKeys.get(0);
            List<String> otherKeys = dailyKeys.subList(1, dailyKeys.size());

            writeTemplate.opsForZSet().unionAndStore(
                firstKey,
                otherKeys,
                destKey,
                Aggregate.SUM,
                Weights.of(1, 1, 1, 1, 1, 1, 1)
            );
            writeTemplate.expire(destKey, RANKING_AGGREGATED_TTL_SECONDS, TimeUnit.SECONDS);

            Long size = writeTemplate.opsForZSet().zCard(destKey);
            log.info("주간 랭킹 생성 완료: {} (members={})", destKey, size);
        } catch (Exception e) {
            log.error("주간 랭킹 생성 실패", e);
        }
    }

    /**
     * Rolling Carry-Over로 월간 랭킹을 생성한다.
     *
     * <p>ZUNIONSTORE(todayMonthly × decayRate, todayDaily × 1.0) → ranking:monthly:{tomorrow}</p>
     * <p>초기화: monthly 키가 없으면 결과 = 0 × decay + todayDaily → daily 복사로 자연 부트스트랩.</p>
     */
    void buildMonthlyRanking(LocalDate today, LocalDate tomorrow) {
        try {
            double decayRate = properties.monthlyDecayRate();
            String todayMonthlyKey = monthlyKey(today);
            String tomorrowMonthlyKey = monthlyKey(tomorrow);
            String todayDailyKey = zsetKey(today);

            writeTemplate.opsForZSet().unionAndStore(
                todayMonthlyKey,
                Collections.singletonList(todayDailyKey),
                tomorrowMonthlyKey,
                Aggregate.SUM,
                Weights.of(decayRate, 1.0)
            );
            trimZset(tomorrowMonthlyKey);
            writeTemplate.expire(tomorrowMonthlyKey, RANKING_AGGREGATED_TTL_SECONDS, TimeUnit.SECONDS);

            Long size = writeTemplate.opsForZSet().zCard(tomorrowMonthlyKey);
            log.info("월간 랭킹 생성 완료: {} (decay={}, members={})",
                tomorrowMonthlyKey, decayRate, size);
        } catch (Exception e) {
            log.error("월간 랭킹 생성 실패", e);
        }
    }
}
