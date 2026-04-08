package com.loopers.infrastructure.ranking;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 랭킹 ZSET Redis 저장소 — 읽기 전용 (commerce-api)
 *
 * REPLICA_PREFERRED 템플릿(기본)을 사용한다.
 * 랭킹 조회는 강한 일관성이 필요 없으므로 Replica 읽기가 적합하다.
 */
@Repository
public class RankingRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public RankingRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 랭킹 상위 N개를 점수와 함께 조회한다 (offset 기반).
     *
     * @param key   ZSET 키 (e.g., "ranking:all:20260408")
     * @param start 시작 인덱스 (0-based, inclusive)
     * @param end   끝 인덱스 (0-based, inclusive)
     * @return (productId, score) 리스트 — 점수 내림차순
     */
    public List<RankingEntry> getTopWithScores(String key, long start, long end) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        return tuples.stream()
                .filter(t -> t.getValue() != null && t.getScore() != null)
                .map(t -> new RankingEntry(Long.parseLong(t.getValue()), t.getScore()))
                .toList();
    }

    /**
     * ZSET의 총 멤버 수를 반환한다.
     */
    public long getSize(String key) {
        Long size = redisTemplate.opsForZSet().zCard(key);
        return size != null ? size : 0;
    }

    /**
     * 특정 상품의 순위를 조회한다 (0-based).
     *
     * @return 0-based 순위. ZSET에 없으면 null.
     */
    public Long getRank(String key, Long productId) {
        return redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
    }

    /**
     * 특정 상품의 점수를 조회한다.
     *
     * @return 점수. ZSET에 없으면 null.
     */
    public Double getScore(String key, Long productId) {
        return redisTemplate.opsForZSet().score(key, String.valueOf(productId));
    }

    /**
     * ZSET에서 조회한 랭킹 항목 (productId + score)
     */
    public record RankingEntry(Long productId, double score) {}
}
