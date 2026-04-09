package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RankingRedisRepository implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public List<RankingEntry> getTopN(String key, int offset, int size) {
        // ZREVRANGE ranking:all:{yyyyMMdd} {offset} {offset+size-1} WITHSCORES
        // score 내림차순으로 상위 N개 조회
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet()
                        .reverseRangeWithScores(key, offset, (long) offset + size - 1);

        if (tuples == null) return List.of();

        // TypedTuple(member, score) → RankingEntry(productId, score) 변환
        return tuples.stream()
                .map(t -> new RankingEntry(
                        Long.valueOf(t.getValue()),   // member(String) → productId(Long)
                        t.getScore() != null ? t.getScore() : 0.0))
                .toList();
    }

    @Override
    public Long getRank(String key, Long productId) {
        // ZREVRANK ranking:all:{yyyyMMdd} {productId}
        // 0-based 순위 반환 (1등=0, 2등=1, ...) -> 없으면 null.
        return redisTemplate.opsForZSet()
                .reverseRank(key, String.valueOf(productId));
    }

    @Override
    public List<RankingEntry> getTopNFromDB(int offset, int size) {
        // DB에서 가중치 합산 점수로 정렬하여 Top-N 조회
        // native query 결과 Object[]{productId(BigInteger), score(Double)} → RankingEntry 변환
        return productMetricsJpaRepository.findTopNByWeightedScore(offset, size).stream()
                .map(row -> new RankingEntry(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).doubleValue()))
                .toList();
    }
}
