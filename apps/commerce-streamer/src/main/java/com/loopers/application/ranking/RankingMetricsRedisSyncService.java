package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingMember;
import com.loopers.domain.ranking.RankingMetricCounts;
import com.loopers.domain.ranking.RankingRedisKeyResolver;
import com.loopers.domain.ranking.RankingScoreCalculator;
import com.loopers.domain.ranking.RankingTtlPolicy;
import com.loopers.domain.ranking.RankingWriteRepository;
import com.loopers.infrastructure.collector.ProductMetricsModel;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * DB {@code product_metrics} 한 행을 기준으로 Redis ZSET에 점수를 덮어쓴다(매트릭 기반 ZADD).
 * <p>
 * 실시간 컨슈머와 보정 배치가 동일한 공식·키 규칙을 쓰도록 한 진입점이다.
 */
@Service
public class RankingMetricsRedisSyncService {

    private final RankingScoreCalculator rankingScoreCalculator;
    private final RankingWriteRepository rankingWriteRepository;
    private final RankingRedisKeyResolver rankingRedisKeyResolver = new RankingRedisKeyResolver();
    private final RankingTtlPolicy rankingTtlPolicy = new RankingTtlPolicy();

    public RankingMetricsRedisSyncService(
            RankingScoreCalculator rankingScoreCalculator,
            RankingWriteRepository rankingWriteRepository) {
        this.rankingScoreCalculator = rankingScoreCalculator;
        this.rankingWriteRepository = rankingWriteRepository;
    }

    /**
     * 매트릭 데이터와 일자 키(occurredAt 기준, Asia/Seoul)로 ZSET을 갱신한다.
     *
     * @param metrics DB에서 읽은 상품 매트릭
     * @param occurredAtForKey 일간 키를 자를 기준 시각(실시간 경로는 이벤트 occurredAt, 보정은 보통 {@code last_event_occurred_at})
     */
    public void upsertFromMetrics(ProductMetricsModel metrics, Instant occurredAtForKey) {
        if (occurredAtForKey == null) {
            return;
        }
        // 매트릭 데이터를 집계한다.
        RankingMetricCounts counts = new RankingMetricCounts(
                metrics.getViewCount(),
                metrics.getLikeCount(),
                metrics.getSoldQuantity());
        double score = rankingScoreCalculator.calculate(counts);
        if (score <= 0.0d) {
            return;
        }
        String key = rankingRedisKeyResolver.resolveDailyAllKey(occurredAtForKey);
        String member = RankingMember.fromProductId(metrics.getProductId());
        rankingWriteRepository.upsertScore(key, member, score, rankingTtlPolicy.dailyKeyTtl());
    }
}
