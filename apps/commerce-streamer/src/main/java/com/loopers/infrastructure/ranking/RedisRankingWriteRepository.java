package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingWriteRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisRankingWriteRepository implements RankingWriteRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingWriteRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 계산된 총점을 랭킹 저장소에 반영한다.
     *
     * @param key 일간 랭킹 키
     * @param member 상품 member 문자열
     * @param score 계산된 총점
     * @param ttl 키 TTL
     * 
     * ttlSeconds() 메서드를 사용하여 키의 TTL을 초 단위로 가져온다.
     * 키의 TTL이 0 이하인 경우, 키의 TTL을 설정한다.
     * 
     */
    @Override
    public void upsertScore(String key, String member, double score, Duration ttl) {
        /** Redis ZADD해 점수를 새 값으로 덮어쓴다. */
        redisTemplate.opsForZSet().add(key, member, score);
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttlSeconds == null || ttlSeconds < 0L) {
            redisTemplate.expire(key, ttl);
        }
    }
}
