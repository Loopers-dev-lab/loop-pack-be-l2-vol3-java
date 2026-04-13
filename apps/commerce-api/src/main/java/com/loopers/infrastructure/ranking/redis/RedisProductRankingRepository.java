package com.loopers.infrastructure.ranking.redis;

import com.loopers.application.ranking.RankingProductView;
import com.loopers.application.ranking.RankingRepository;
import com.loopers.config.redis.RedisConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class RedisProductRankingRepository implements RankingRepository {

    private static final DateTimeFormatter KEY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter KEY_HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final Duration DAILY_RANKING_TTL = Duration.ofHours(48);
    private static final Duration HOURLY_RANKING_TTL = Duration.ofHours(2);
    private static final Duration WEEKLY_RANKING_TTL = Duration.ofDays(90);
    private static final Duration MONTHLY_RANKING_TTL = Duration.ofDays(400);
    private static final String WEEKLY_KEY_PREFIX = "ranking:weekly:";
    private static final String MONTHLY_KEY_PREFIX = "ranking:monthly:";

    private final RedisTemplate<String, String> redisTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public RedisProductRankingRepository(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankingProductView> findDailyPage(LocalDate metricDate, int page, int size) {
        return findPage(buildDailyRankingKey(metricDate), page, size);
    }

    @Override
    public List<RankingProductView> findHourlyPage(LocalDateTime metricHour, int page, int size) {
        return findPage(buildHourlyRankingKey(metricHour), page, size);
    }

    @Override
    public List<RankingProductView> findWeeklyPage(LocalDate periodStartDate, int page, int size) {
        String rankingKey = buildWeeklyRankingKey(periodStartDate);
        List<RankingProductView> cachedRankings = findPage(rankingKey, page, size);
        if (!cachedRankings.isEmpty() || exists(rankingKey)) {
            return cachedRankings;
        }
        List<RankingProductView> loadedRankings = findWeeklyPageFromDatabase(periodStartDate, page, size);
        if (!loadedRankings.isEmpty()) {
            cacheWeeklyRanking(periodStartDate);
        }
        return loadedRankings;
    }

    @Override
    public List<RankingProductView> findMonthlyPage(LocalDate periodStartDate, int page, int size) {
        String rankingKey = buildMonthlyRankingKey(periodStartDate);
        List<RankingProductView> cachedRankings = findPage(rankingKey, page, size);
        if (!cachedRankings.isEmpty() || exists(rankingKey)) {
            return cachedRankings;
        }
        List<RankingProductView> loadedRankings = findMonthlyPageFromDatabase(periodStartDate, page, size);
        if (!loadedRankings.isEmpty()) {
            cacheMonthlyRanking(periodStartDate);
        }
        return loadedRankings;
    }

    @Override
    public RankingProductView findProductRank(LocalDate metricDate, UUID productId) {
        String member = productId.toString();
        Long rank = redisTemplate.opsForZSet().reverseRank(buildDailyRankingKey(metricDate), member);
        if (rank == null) {
            return null;
        }
        Double score = redisTemplate.opsForZSet().score(buildDailyRankingKey(metricDate), member);
        return new RankingProductView(productId, rank + 1L, score);
    }

    public String buildDailyRankingKey(LocalDate metricDate) {
        return "ranking:all:" + metricDate.format(KEY_DATE_FORMATTER);
    }

    public String buildHourlyRankingKey(LocalDateTime metricHour) {
        return "ranking:hourly:" + metricHour.format(KEY_HOUR_FORMATTER);
    }

    public String buildWeeklyRankingKey(LocalDate periodStartDate) {
        return WEEKLY_KEY_PREFIX + periodStartDate.format(KEY_DATE_FORMATTER);
    }

    public String buildMonthlyRankingKey(LocalDate periodStartDate) {
        return MONTHLY_KEY_PREFIX + periodStartDate.format(KEY_DATE_FORMATTER);
    }

    public void warmUpWeeklyRankings(List<LocalDate> periodStartDates) {
        periodStartDates.forEach(this::cacheWeeklyRanking);
    }

    public void warmUpMonthlyRankings(List<LocalDate> periodStartDates) {
        periodStartDates.forEach(this::cacheMonthlyRanking);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void replaceWeeklyRanking(LocalDate periodStartDate, Map<String, Double> rankingScores) {
        replaceRanking(buildWeeklyRankingKey(periodStartDate), WEEKLY_RANKING_TTL, rankingScores);
    }

    public void replaceMonthlyRanking(LocalDate periodStartDate, Map<String, Double> rankingScores) {
        replaceRanking(buildMonthlyRankingKey(periodStartDate), MONTHLY_RANKING_TTL, rankingScores);
    }

    public void cacheWeeklyRanking(LocalDate periodStartDate) {
        if (exists(buildWeeklyRankingKey(periodStartDate))) {
            return;
        }
        replaceWeeklyRanking(periodStartDate, loadTop100Scores(
                "product_ranking_weekly_batch",
                periodStartDate
        ));
    }

    public void cacheMonthlyRanking(LocalDate periodStartDate) {
        if (exists(buildMonthlyRankingKey(periodStartDate))) {
            return;
        }
        replaceMonthlyRanking(periodStartDate, loadTop100Scores(
                "product_ranking_monthly_batch",
                periodStartDate
        ));
    }

    private List<RankingProductView> findPage(String rankingKey, int page, int size) {
        long start = (long) (page - 1) * size;
        long end = start + size - 1L;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(rankingKey, start, end);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        AtomicLong rank = new AtomicLong(start + 1L);
        return tuples.stream()
                .map(tuple -> new RankingProductView(
                        UUID.fromString(tuple.getValue()),
                        rank.getAndIncrement(),
                        tuple.getScore()
                ))
                .toList();
    }

    private List<RankingProductView> findWeeklyPageFromDatabase(LocalDate periodStartDate, int page, int size) {
        return findPeriodPageFromDatabase("product_ranking_weekly_batch", periodStartDate, page, size);
    }

    private List<RankingProductView> findMonthlyPageFromDatabase(LocalDate periodStartDate, int page, int size) {
        return findPeriodPageFromDatabase("product_ranking_monthly_batch", periodStartDate, page, size);
    }

    private List<RankingProductView> findPeriodPageFromDatabase(String tableName, LocalDate periodStartDate, int page, int size) {
        int offset = (page - 1) * size;
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                        """
                        SELECT product_id, ranking_score
                        FROM %s
                        WHERE period_start_date = :periodStartDate
                        ORDER BY ranking_score DESC, product_id ASC
                        LIMIT %d OFFSET %d
                        """.formatted(tableName, size, offset)
                )
                .setParameter("periodStartDate", periodStartDate)
                .getResultList();

        AtomicLong rank = new AtomicLong((long) offset + 1L);
        return rows.stream()
                .map(row -> new RankingProductView(
                        UUID.fromString((String) row[0]),
                        rank.getAndIncrement(),
                        ((Number) row[1]).doubleValue()
                ))
                .toList();
    }

    private Map<String, Double> loadTop100Scores(String tableName, LocalDate periodStartDate) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                        """
                        SELECT product_id, ranking_score
                        FROM %s
                        WHERE period_start_date = :periodStartDate
                        ORDER BY ranking_score DESC, product_id ASC
                        LIMIT 100
                        """.formatted(tableName)
                )
                .setParameter("periodStartDate", periodStartDate)
                .getResultList();

        Map<String, Double> rankingScores = new LinkedHashMap<>();
        rows.forEach(row -> rankingScores.put((String) row[0], ((Number) row[1]).doubleValue()));
        return rankingScores;
    }

    private void replaceRanking(String rankingKey, Duration ttl, Map<String, Double> rankingScores) {
        String tempRankingKey = rankingKey + ":sync";
        redisTemplate.delete(tempRankingKey);
        if (rankingScores.isEmpty()) {
            return;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = rankingScores.entrySet().stream()
                .map(entry -> ZSetOperations.TypedTuple.of(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.toSet());
        redisTemplate.opsForZSet().add(tempRankingKey, tuples);
        redisTemplate.expire(tempRankingKey, ttl);
        redisTemplate.rename(tempRankingKey, rankingKey);
        redisTemplate.expire(rankingKey, ttl);
    }
}
