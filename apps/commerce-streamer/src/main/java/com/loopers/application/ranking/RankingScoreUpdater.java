package com.loopers.application.ranking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MetricsDelta를 Redis 랭킹(Hash + ZSET)에 반영한다.
 *
 * <p>Pipeline 2회: HINCRBY(Hash 누적) → 리턴값으로 score 계산 → ZADD(ZSET 덮어쓰기).
 * ZINCRBY 대신 HINCRBY→ZADD를 선택한 근거는 설계 문서 참조.</p>
 */
@Slf4j
@Component
public class RankingScoreUpdater {

    public static final String RANKING_ZSET_PREFIX = "ranking:all:";
    public static final String RANKING_METRICS_PREFIX = "ranking:metrics:";
    public static final long RANKING_TTL_SECONDS = 172_800L; // 2일
    static final double TIEBREAKER_EPSILON = 1e-10;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final RedisTemplate<String, String> writeTemplate;
    private final RankingProperties properties;

    public RankingScoreUpdater(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate,
        RankingProperties properties
    ) {
        this.writeTemplate = writeTemplate;
        this.properties = properties;
    }

    static String zsetKey(LocalDate date) {
        return RANKING_ZSET_PREFIX + date.format(DATE_FORMATTER);
    }

    static String hashKey(LocalDate date, Long productId) {
        return RANKING_METRICS_PREFIX + date.format(DATE_FORMATTER) + ":" + productId;
    }

    public void update(Map<Long, MetricsDelta> deltaMap) {
        if (deltaMap.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now(KST);
        String zsetKey = zsetKey(today);

        Map<Long, long[]> accumulated = pipelineHincrby(deltaMap, today);
        pipelineZadd(accumulated, zsetKey);

        log.debug("랭킹 스코어 갱신: date={}, products={}", today.format(DATE_FORMATTER), deltaMap.size());
    }

    /**
     * Pipeline 1: productId당 4 HINCRBY + 1 EXPIRE.
     * 리턴 순서에 의존하여 누적치를 파싱한다.
     */
    @SuppressWarnings("unchecked")
    private Map<Long, long[]> pipelineHincrby(Map<Long, MetricsDelta> deltaMap, LocalDate date) {
        List<Long> productIds = new ArrayList<>(deltaMap.keySet());

        List<Object> results = writeTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (Long productId : productIds) {
                    MetricsDelta delta = deltaMap.get(productId);
                    String hKey = hashKey(date, productId);

                    operations.opsForHash().increment(hKey, "viewCount", (long) delta.getViewDelta());
                    operations.opsForHash().increment(hKey, "likeCount", (long) delta.getNetLikeDelta());
                    operations.opsForHash().increment(hKey, "salesCount", (long) delta.getNetSalesCountDelta());
                    operations.opsForHash().increment(hKey, "salesAmount", delta.getNetSalesAmountDelta());
                    operations.expire(hKey, RANKING_TTL_SECONDS, TimeUnit.SECONDS);
                }
                return null;
            }
        });

        // productId당 5개 결과 (4 HINCRBY + 1 EXPIRE)
        Map<Long, long[]> accumulated = new HashMap<>();
        for (int i = 0; i < productIds.size(); i++) {
            int base = i * 5;
            long viewCount = toLong(results.get(base));
            long likeCount = toLong(results.get(base + 1));
            long salesCount = toLong(results.get(base + 2));
            long salesAmount = toLong(results.get(base + 3));

            accumulated.put(productIds.get(i), new long[]{viewCount, likeCount, salesCount, salesAmount});
        }
        return accumulated;
    }

    @SuppressWarnings("unchecked")
    private void pipelineZadd(Map<Long, long[]> accumulated, String zsetKey) {
        writeTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (Map.Entry<Long, long[]> entry : accumulated.entrySet()) {
                    Long productId = entry.getKey();
                    long[] counts = entry.getValue();
                    warnIfNegative(productId, counts);
                    double score = calculateScore(counts[0], counts[1], counts[3], productId);
                    operations.opsForZSet().add(zsetKey, String.valueOf(entry.getKey()), score);
                }
                operations.expire(zsetKey, RANKING_TTL_SECONDS, TimeUnit.SECONDS);
                return null;
            }
        });
    }

    // score = W(view)×log₁₀(viewCount+1) + W(like)×log₁₀(likeCount+1) + W(order)×log₁₀(salesAmount+1) + productId×ε
    double calculateScore(long viewCount, long likeCount, long salesAmount, long productId) {
        RankingProperties.Weights w = properties.weights();
        return w.view() * Math.log10(Math.max(0, viewCount) + 1)
            + w.like() * Math.log10(Math.max(0, likeCount) + 1)
            + w.order() * Math.log10(Math.max(0, salesAmount) + 1)
            + productId * TIEBREAKER_EPSILON;
    }

    private void warnIfNegative(Long productId, long[] counts) {
        if (counts[0] < 0 || counts[1] < 0 || counts[3] < 0) {
            log.warn("음수 메트릭 감지: productId={}, view={}, like={}, salesAmount={}",
                productId, counts[0], counts[1], counts[3]);
        }
    }

    private static long toLong(Object result) {
        if (result instanceof Long l) return l;
        if (result instanceof Number n) return n.longValue();
        return 0L;
    }
}
