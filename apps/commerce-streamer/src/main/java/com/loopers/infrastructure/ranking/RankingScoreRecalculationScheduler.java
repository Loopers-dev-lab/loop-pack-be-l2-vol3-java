package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class RankingScoreRecalculationScheduler {

    private static final String ALL_KEY_PREFIX = "ranking:all:";
    private static final String RAW_KEY_PREFIX = "ranking:raw:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final long TTL_DAYS = 2;

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.6;
    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final StringRedisTemplate redisTemplate;

    public RankingScoreRecalculationScheduler(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedRate = 300_000)
    public void recalculate() {
        LocalDate today = LocalDate.now();
        String date = today.format(DATE_FORMAT);
        String rawKey = RAW_KEY_PREFIX + date;
        String allKey = ALL_KEY_PREFIX + date;

        // 1. raw 카운트 읽기
        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(rawKey);
        if (rawData.isEmpty()) {
            return;
        }

        // 2. 상품별 raw 메트릭 파싱
        Map<String, ProductRawMetrics> metricsMap = parseRawMetrics(rawData);
        if (metricsMap.isEmpty()) {
            return;
        }

        // 3. 전일 carry-over 점수 읽기
        String yesterdayAllKey = ALL_KEY_PREFIX + today.minusDays(1).format(DATE_FORMAT);
        Map<String, Double> yesterdayScores = loadYesterdayScores(yesterdayAllKey);

        // 4. 메인 점수 계산 (raw 기반 + carry-over)
        List<ProductMetrics> metricsList = new ArrayList<>();
        for (Map.Entry<String, ProductRawMetrics> entry : metricsMap.entrySet()) {
            String pid = entry.getKey();
            ProductRawMetrics raw = entry.getValue();
            double mainScore = raw.views * VIEW_WEIGHT + raw.likes * LIKE_WEIGHT
                    + Math.log1p(raw.orders) * ORDER_WEIGHT;

            // carry-over: 전일 최종 점수의 정수부 * 0.1
            Double yesterdayScore = yesterdayScores.get(pid);
            if (yesterdayScore != null) {
                mainScore += Math.floor(yesterdayScore) * CARRY_OVER_WEIGHT;
            }

            metricsList.add(new ProductMetrics(pid, mainScore, raw.views, raw.likes, raw.lastEvent));
        }

        // 5. 타이브레이커 정규화
        long now = Instant.now().getEpochSecond();
        double[] viewArr = metricsList.stream().mapToDouble(m -> m.views).toArray();
        double[] likeArr = metricsList.stream().mapToDouble(m -> m.likes).toArray();
        double[] recencyArr = metricsList.stream().mapToDouble(m ->
                m.lastEvent > 0 ? now - m.lastEvent : Double.MAX_VALUE
        ).toArray();

        double viewMean = mean(viewArr);
        double viewStd = stddev(viewArr, viewMean);
        double likeMean = mean(likeArr);
        double likeStd = stddev(likeArr, likeMean);
        double recencyMean = mean(recencyArr);
        double recencyStd = stddev(recencyArr, recencyMean);

        // 6. 최종 스코어 인코딩: 정수부(메인) + 소수부(타이브레이커)
        Set<ZSetOperations.TypedTuple<String>> finalScores = new HashSet<>();
        for (int i = 0; i < metricsList.size(); i++) {
            ProductMetrics m = metricsList.get(i);

            int viewNorm = normalize(m.views, viewMean, viewStd);
            int likeNorm = normalize(m.likes, likeMean, likeStd);
            int recencyNorm = 999 - normalize(recencyArr[i], recencyMean, recencyStd);

            double tieBreaker = viewNorm / 1_000.0
                    + likeNorm / 1_000_000.0
                    + recencyNorm / 1_000_000_000.0;

            double finalScore = Math.floor(m.mainScore) + tieBreaker;
            finalScores.add(ZSetOperations.TypedTuple.of(m.productId, finalScore));
        }

        redisTemplate.opsForZSet().add(allKey, finalScores);
        redisTemplate.expire(allKey, Duration.ofDays(TTL_DAYS));

        log.info("랭킹 스코어 재계산 완료: {}개 상품 (carry-over 포함)", metricsList.size());
    }

    private Map<String, ProductRawMetrics> parseRawMetrics(Map<Object, Object> rawData) {
        Map<String, ProductRawMetrics> metricsMap = new HashMap<>();

        for (Map.Entry<Object, Object> entry : rawData.entrySet()) {
            String field = entry.getKey().toString();
            String value = entry.getValue().toString();
            int colonIdx = field.lastIndexOf(':');
            if (colonIdx < 0) continue;

            String pid = field.substring(0, colonIdx);
            String type = field.substring(colonIdx + 1);

            ProductRawMetrics metrics = metricsMap.computeIfAbsent(pid, k -> new ProductRawMetrics());
            switch (type) {
                case "v" -> metrics.views = Long.parseLong(value);
                case "l" -> metrics.likes = Long.parseLong(value);
                case "o" -> metrics.orders = Long.parseLong(value);
                case "t" -> metrics.lastEvent = Long.parseLong(value);
            }
        }

        return metricsMap;
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

    private static class ProductRawMetrics {
        long views;
        long likes;
        long orders;
        long lastEvent;
    }

    private record ProductMetrics(
            String productId,
            double mainScore,
            long views,
            long likes,
            long lastEvent
    ) {}
}
