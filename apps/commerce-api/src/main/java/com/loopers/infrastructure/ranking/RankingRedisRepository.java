package com.loopers.infrastructure.ranking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 랭킹 ZSET Redis 저장소 — 읽기 전용 (commerce-api)
 *
 * REPLICA_PREFERRED 템플릿(기본)을 사용한다.
 * 랭킹 조회는 강한 일관성이 필요 없으므로 Replica 읽기가 적합하다.
 * ZUNIONSTORE(쓰기)는 Lettuce가 자동으로 Master로 라우팅한다.
 */
@Repository
public class RankingRedisRepository {

    private static final Logger log = LoggerFactory.getLogger(RankingRedisRepository.class);

    /**
     * Score Carry-Over Lua 스크립트
     *
     * KEYS[1] = destination key (내일)
     * KEYS[2] = source key (오늘)
     * ARGV[1] = weight (e.g., "0.1")
     * ARGV[2] = ttl in seconds
     *
     * 오늘 키가 존재하면 내일 키에 점수 × weight로 복사한다.
     * 내일 키에 TTL이 없으면 설정한다.
     */
    private static final String CARRY_OVER_SCRIPT =
            "local exists = redis.call('EXISTS', KEYS[2]) " +
            "if exists == 0 then return 0 end " +
            "redis.call('ZUNIONSTORE', KEYS[1], 1, KEYS[2], 'WEIGHTS', ARGV[1]) " +
            "local ttl = redis.call('TTL', KEYS[1]) " +
            "if ttl == -1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end " +
            "return redis.call('ZCARD', KEYS[1])";

    private static final DefaultRedisScript<Long> CARRY_OVER_REDIS_SCRIPT;

    static {
        CARRY_OVER_REDIS_SCRIPT = new DefaultRedisScript<>();
        CARRY_OVER_REDIS_SCRIPT.setScriptText(CARRY_OVER_SCRIPT);
        CARRY_OVER_REDIS_SCRIPT.setResultType(Long.class);
    }

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
     * Score Carry-Over: 소스 키의 점수를 가중치를 곱해 대상 키에 복사한다.
     *
     * ZUNIONSTORE destKey 1 srcKey WEIGHTS weight
     *
     * @param destKey    대상 키 (내일)
     * @param srcKey     소스 키 (오늘)
     * @param weight     가중치 (e.g., 0.1 → 오늘 점수의 10%)
     * @param ttlSeconds 대상 키의 TTL
     * @return 복사된 멤버 수. 소스가 없으면 0.
     */
    public long carryOver(String destKey, String srcKey, double weight, long ttlSeconds) {
        Long result = redisTemplate.execute(
                CARRY_OVER_REDIS_SCRIPT,
                List.of(destKey, srcKey),
                String.valueOf(weight),
                String.valueOf(ttlSeconds)
        );
        return result != null ? result : 0;
    }

    /**
     * ZSET에서 조회한 랭킹 항목 (productId + score)
     */
    public record RankingEntry(Long productId, double score) {}
}
