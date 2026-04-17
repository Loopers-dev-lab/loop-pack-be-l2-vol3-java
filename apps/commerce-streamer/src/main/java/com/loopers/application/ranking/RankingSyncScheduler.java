package com.loopers.application.ranking;

import com.loopers.config.redis.RankingKeys;
import com.loopers.domain.ranking.RankingMetricsSummary;
import com.loopers.domain.ranking.RankingMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * dirty 플래그 기반 랭킹 Redis 동기화 스케줄러.
 *
 * 동작 흐름 (5초 주기):
 * 1. dirty=true인 (productId, hour) 쌍을 productId 기준으로 그룹핑
 * 2. productId당 1회: 오늘 전체 시간대 SUM × weight → ZADD 일간 랭킹 (덮어쓰기)
 * 3. (productId, hour)당 1회: 해당 시간대 SUM × weight → ZADD 시간별 랭킹 (덮어쓰기)
 * 4. 성공한 (productId, hour) 행만 dirty=false 전환
 *
 * ZADD 덮어쓰기이므로 멱등성 보장. 스케줄러 재시작 시 안전하게 재처리 가능.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RankingSyncScheduler {

    private final RankingMetricsService rankingMetricsService;
    private final RankingWeightCacheService rankingWeightCacheService;
    private final RedisTemplate<String, String> masterRedisTemplate;

    @Scheduled(fixedDelay = 5000)
    public void sync() {
        try {
            LocalDate today = LocalDate.now();

            Map<Long, List<Integer>> dirtyByProduct =
                    rankingMetricsService.findDirtyEntriesGroupedByProduct(today);

            if (dirtyByProduct.isEmpty()) return;

            // weight 조회 (Redis Cache-Aside, TTL 300s)
            BigDecimal viewWeight = rankingWeightCacheService.getWeight("VIEW");
            BigDecimal likeWeight = rankingWeightCacheService.getWeight("LIKE");
            BigDecimal orderWeight = rankingWeightCacheService.getWeight("ORDER");

            String dailyKey = RankingKeys.dailyKey(today);
            Duration ttl = RankingKeys.dailyTtl(today);

            for (Map.Entry<Long, List<Integer>> entry : dirtyByProduct.entrySet()) {
                Long productId = entry.getKey();
                List<Integer> dirtyHours = entry.getValue();

                try {
                    // 일간 랭킹 갱신 (productId당 1회 — 전체 시간대 SUM)
                    RankingMetricsSummary daily = rankingMetricsService.sumByProductIdAndDate(productId, today);
                    double dailyScore = calculateScore(daily, viewWeight, likeWeight, orderWeight);
                    masterRedisTemplate.opsForZSet().add(dailyKey, String.valueOf(productId), dailyScore);
                    masterRedisTemplate.expire(dailyKey, ttl);

                    // 시간별 랭킹 갱신 + dirty 해제 (dirty hour당 1회)
                    for (int hour : dirtyHours) {
                        try {
                            RankingMetricsSummary hourly =
                                    rankingMetricsService.sumByProductIdAndDateAndHour(productId, today, hour);
                            double hourlyScore = calculateScore(hourly, viewWeight, likeWeight, orderWeight);
                            String hourlyKey = RankingKeys.hourlyKey(today, hour);
                            masterRedisTemplate.opsForZSet().add(hourlyKey, String.valueOf(productId), hourlyScore);
                            masterRedisTemplate.expire(hourlyKey, ttl);

                            rankingMetricsService.clearDirtyByHour(productId, today, hour);
                        } catch (Exception e) {
                            log.error("[RankingSyncScheduler] 시간별 처리 실패: productId={}, hour={}", productId, hour, e);
                        }
                    }
                } catch (Exception e) {
                    // 상품 하나 실패해도 나머지 처리 계속
                    log.error("[RankingSyncScheduler] 상품 처리 실패: productId={}", productId, e);
                }
            }

            log.debug("[RankingSyncScheduler] 동기화 완료: {}개 상품", dirtyByProduct.size());

        } catch (Exception e) {
            // 스케줄러 스레드 보호 — 예외가 밖으로 나가면 @Scheduled가 멈춤
            log.error("[RankingSyncScheduler] 스케줄러 오류", e);
        }
    }

    /**
     * log 정규화 기반 score 계산.
     * log(1 + x)로 극단값 억제. revenue=0인 상품(조회/좋아요만 있는 경우)도 안전하게 0 처리.
     */
    private double calculateScore(RankingMetricsSummary summary,
                                  BigDecimal viewWeight, BigDecimal likeWeight, BigDecimal orderWeight) {
        double viewScore = Math.log1p(summary.totalViewCount()) * viewWeight.doubleValue();
        double likeScore = Math.log1p(summary.totalLikeCount()) * likeWeight.doubleValue();
        double orderScore = Math.log1p(summary.totalOrderRevenue().doubleValue()) * orderWeight.doubleValue();
        return viewScore + likeScore + orderScore;
    }
}
