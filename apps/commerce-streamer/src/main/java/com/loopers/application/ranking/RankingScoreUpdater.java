package com.loopers.application.ranking;

import com.loopers.domain.ranking.ScoreFormula;
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
 *
 * @see ScoreFormula
 */
@Slf4j
@Component
public class RankingScoreUpdater {

    public static final String RANKING_ZSET_PREFIX = "ranking:all:";
    public static final String RANKING_METRICS_PREFIX = "ranking:metrics:";

    /** Daily ZSET TTL: 8일 (배치 보정에 최근 데이터 필요 + 여유) */
    public static final long RANKING_ZSET_TTL_SECONDS = 691_200L;
    /** Hash TTL: 2일 (당일 score 재계산에만 사용) */
    public static final long RANKING_HASH_TTL_SECONDS = 172_800L;

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

    static String zsetKey(String prefix, LocalDate date) {
        return prefix + date.format(DATE_FORMATTER);
    }

    static String hashKey(LocalDate date, Long productId) {
        return RANKING_METRICS_PREFIX + date.format(DATE_FORMATTER) + ":" + productId;
    }

    public void update(Map<Long, MetricsDelta> deltaMap) {
        if (deltaMap.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now(KST);

        // Pipeline 1: Hash 누적 (메트릭은 variant 무관, 1회만 실행)
        Map<Long, long[]> accumulated = pipelineHincrby(deltaMap, today);

        // Pipeline 2: ZSET 쓰기
        RankingProperties.Experiment experiment = properties.experiment();
        if (experiment.enabled() && !experiment.variants().isEmpty()) {
            // A/B 테스트: 각 variant별로 다른 weights + zsetPrefix로 ZADD
            for (RankingProperties.Variant variant : experiment.variants().values()) {
                String variantZsetKey = zsetKey(variant.zsetPrefix(), today);
                pipelineZadd(accumulated, variantZsetKey, variant.weights(), deltaMap);
            }
        } else {
            // 기본 모드: 단일 ZSET
            String zsetKey = zsetKey(today);
            pipelineZadd(accumulated, zsetKey, properties.weights(), deltaMap);
        }

        log.debug("랭킹 스코어 갱신: date={}, products={}", today.format(DATE_FORMATTER), deltaMap.size());
    }

    /**
     * Pipeline 1: productId당 4 HINCRBY + 1 HSET(lastEventAt) + 1 EXPIRE.
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
                    operations.opsForHash().put(hKey, "lastEventAt", String.valueOf(delta.getLastEventEpochSeconds()));
                    operations.expire(hKey, RANKING_HASH_TTL_SECONDS, TimeUnit.SECONDS);
                }
                return null;
            }
        });

        // productId당 6개 결과 (4 HINCRBY + 1 HSET + 1 EXPIRE)
        Map<Long, long[]> accumulated = new HashMap<>();
        for (int i = 0; i < productIds.size(); i++) {
            int base = i * 6;
            long viewCount = toLong(results.get(base));
            long likeCount = toLong(results.get(base + 1));
            long salesCount = toLong(results.get(base + 2));
            long salesAmount = toLong(results.get(base + 3));

            accumulated.put(productIds.get(i), new long[]{viewCount, likeCount, salesCount, salesAmount});
        }
        return accumulated;
    }

    @SuppressWarnings("unchecked")
    private void pipelineZadd(Map<Long, long[]> accumulated, String zsetKey,
                              ScoreFormula.Weights weights, Map<Long, MetricsDelta> deltaMap) {
        writeTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (Map.Entry<Long, long[]> entry : accumulated.entrySet()) {
                    Long productId = entry.getKey();
                    long[] counts = entry.getValue();
                    warnIfNegative(productId, counts);

                    MetricsDelta delta = deltaMap.get(productId);
                    long lastEventAt = delta.getLastEventEpochSeconds();
                    int categoryPriority = properties.categoryPriority()
                        .getOrDefault(0L, properties.defaultCategoryPriority());

                    double score = ScoreFormula.calculate(counts[0], counts[1], counts[3],
                        categoryPriority, lastEventAt, weights);
                    operations.opsForZSet().add(zsetKey, String.valueOf(productId), score);
                }
                operations.expire(zsetKey, RANKING_ZSET_TTL_SECONDS, TimeUnit.SECONDS);
                return null;
            }
        });
    }

    double calculateScore(long viewCount, long likeCount, long salesAmount,
                          long lastEventEpochSeconds, int categoryPriority) {
        return ScoreFormula.calculate(viewCount, likeCount, salesAmount,
            categoryPriority, lastEventEpochSeconds, properties.weights());
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
