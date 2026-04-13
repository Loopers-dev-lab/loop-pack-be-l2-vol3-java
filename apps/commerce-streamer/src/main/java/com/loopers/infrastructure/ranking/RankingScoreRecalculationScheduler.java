package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.metrics.ProductDailyMetrics;
import com.loopers.domain.metrics.ProductDailyMetricsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class RankingScoreRecalculationScheduler {

    private static final String ALL_KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final long TTL_DAYS = 2;

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.6;
    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final StringRedisTemplate redisTemplate;
    private final ProductDailyMetricsRepository productDailyMetricsRepository;

    public RankingScoreRecalculationScheduler(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) StringRedisTemplate redisTemplate,
            ProductDailyMetricsRepository productDailyMetricsRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.productDailyMetricsRepository = productDailyMetricsRepository;
    }

    @Scheduled(fixedRate = 300_000)
    @Transactional(readOnly = true)
    public void recalculate() {
        LocalDate today = LocalDate.now();
        String date = today.format(DATE_FORMAT);
        String allKey = ALL_KEY_PREFIX + date;

        // 1. DB에서 일간 메트릭 읽기 (SSOT)
        List<ProductDailyMetrics> dailyMetrics = productDailyMetricsRepository.findByMetricDate(today);
        if (dailyMetrics.isEmpty()) {
            return;
        }

        // 2. 전일 carry-over 점수 읽기
        String yesterdayAllKey = ALL_KEY_PREFIX + today.minusDays(1).format(DATE_FORMAT);
        Map<String, Double> yesterdayScores = loadYesterdayScores(yesterdayAllKey);

        // 3. 메인 점수 계산 (DB 기반 + carry-over)
        List<ProductScoreEntry> entries = new ArrayList<>();
        for (ProductDailyMetrics dm : dailyMetrics) {
            String pid = String.valueOf(dm.getProductId());
            double mainScore = dm.getViewCount() * VIEW_WEIGHT
                    + dm.getLikeCount() * LIKE_WEIGHT
                    + Math.log1p(dm.getOrderAmount()) * ORDER_WEIGHT;

            Double yesterdayScore = yesterdayScores.get(pid);
            if (yesterdayScore != null) {
                mainScore += Math.floor(yesterdayScore) * CARRY_OVER_WEIGHT;
            }

            long lastEvent = dm.getUpdatedAt() != null
                    ? dm.getUpdatedAt().toInstant().getEpochSecond()
                    : 0;

            entries.add(new ProductScoreEntry(pid, mainScore, dm.getViewCount(), dm.getLikeCount(), lastEvent));
        }

        // 4. 타이브레이커 정규화
        long now = Instant.now().getEpochSecond();
        double[] viewArr = entries.stream().mapToDouble(e -> e.views).toArray();
        double[] likeArr = entries.stream().mapToDouble(e -> e.likes).toArray();
        double[] recencyArr = entries.stream().mapToDouble(e ->
                e.lastEvent > 0 ? now - e.lastEvent : Double.MAX_VALUE
        ).toArray();

        double viewMean = mean(viewArr);
        double viewStd = stddev(viewArr, viewMean);
        double likeMean = mean(likeArr);
        double likeStd = stddev(likeArr, likeMean);
        double recencyMean = mean(recencyArr);
        double recencyStd = stddev(recencyArr, recencyMean);

        // 5. 최종 스코어 인코딩: 정수부(메인) + 소수부(타이브레이커)
        Set<ZSetOperations.TypedTuple<String>> finalScores = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            ProductScoreEntry e = entries.get(i);

            int viewNorm = normalize(e.views, viewMean, viewStd);
            int likeNorm = normalize(e.likes, likeMean, likeStd);
            int recencyNorm = 999 - normalize(recencyArr[i], recencyMean, recencyStd);

            double tieBreaker = viewNorm / 1_000.0
                    + likeNorm / 1_000_000.0
                    + recencyNorm / 1_000_000_000.0;

            double finalScore = Math.floor(e.mainScore) + tieBreaker;
            finalScores.add(ZSetOperations.TypedTuple.of(e.productId, finalScore));
        }

        redisTemplate.opsForZSet().add(allKey, finalScores);
        redisTemplate.expire(allKey, Duration.ofDays(TTL_DAYS));

        log.info("랭킹 스코어 재계산 완료: {}개 상품 (DB SSOT + carry-over)", entries.size());
    }

    private Map<String, Double> loadYesterdayScores(String yesterdayKey) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().rangeWithScores(yesterdayKey, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> scores = new HashMap<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            scores.put(tuple.getValue(), tuple.getScore());
        }
        return scores;
    }

    private double mean(double[] values) {
        if (values.length == 0) return 0;
        double sum = 0;
        int count = 0;
        for (double v : values) {
            if (v != Double.MAX_VALUE) {
                sum += v;
                count++;
            }
        }
        return count > 0 ? sum / count : 0;
    }

    private double stddev(double[] values, double mean) {
        if (values.length <= 1) return 1;
        double sumSq = 0;
        for (double v : values) {
            double diff = (v == Double.MAX_VALUE ? mean : v) - mean;
            sumSq += diff * diff;
        }
        return Math.max(Math.sqrt(sumSq / values.length), 1);
    }

    private int normalize(double value, double mean, double stddev) {
        double z = (value - mean) / stddev;
        double sigmoid = 1.0 / (1.0 + Math.exp(-z));
        return (int) Math.round(sigmoid * 999);
    }

    private record ProductScoreEntry(
            String productId,
            double mainScore,
            long views,
            long likes,
            long lastEvent
    ) {}
}
