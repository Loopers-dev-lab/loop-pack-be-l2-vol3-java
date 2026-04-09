package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingCacheProperties;
import com.loopers.domain.ranking.RankingWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * `RankingWriter` 의 Redis 구현.
 *
 * <p>`masterRedisTemplate` 을 사용한다 — 배치 리스너가 방금 ZADD 한 값을 즉시
 * 스케줄러/Reader 가 읽어야 하므로 replica 복제 지연 회피가 필요하다.
 *
 * <p>TTL 전략: 매 `upsertScore` 호출 시 `expire` 를 함께 실행한다. Redis `EXPIRE` 는
 * O(1) 이며 기존 TTL 을 덮어쓴다. key 가 date-scoped 이므로 "마지막 이벤트 시각 + retention"
 * 으로 자연 만료된다.
 *
 * <p>이전에 JVM 캐시로 EXPIRE 호출을 키별 1회로 줄이는 최적화를 시도했으나, 통합 테스트가
 * Redis flush 후 새 키를 사용해도 in-memory marker 는 남아 EXPIRE 가 스킵되는 문제가
 * 발견되어 원복함. 실측 이득이 미미한 반면 테스트 안정성을 해치는 트레이드오프.
 */
@Component
public class RedisRankingWriter implements RankingWriter {

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RankingCacheProperties cacheProperties;

    public RedisRankingWriter(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
            RankingCacheProperties cacheProperties
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public void upsertScore(String key, Long productId, double score) {
        if (key == null || productId == null) {
            throw new IllegalArgumentException("key/productId must not be null");
        }
        masterRedisTemplate.opsForZSet().add(key, productId.toString(), score);
        if (cacheProperties.retention() != null) {
            masterRedisTemplate.expire(key, cacheProperties.retention());
        }
    }
}
