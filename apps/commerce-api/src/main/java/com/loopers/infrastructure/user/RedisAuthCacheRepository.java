package com.loopers.infrastructure.user;

import com.loopers.domain.user.AuthCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 기반 인증 캐시 구현.
 *
 * <p>읽기는 Replica 우선, 쓰기는 Master로 분산하여
 * 대기열 ZADD/ZPOPMIN과의 경합을 줄인다.</p>
 *
 * <p>Redis 장애 시 null 반환 또는 저장 실패를 무시하여 DB fallback을 유도한다.</p>
 */
@Slf4j
@Component
public class RedisAuthCacheRepository implements AuthCacheRepository {

    private final RedisTemplate<String, String> redisTemplateMaster;
    private final RedisTemplate<String, String> redisTemplateReadOnly;

    public RedisAuthCacheRepository(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplateMaster,
            RedisTemplate<String, String> redisTemplateReadOnly) {
        this.redisTemplateMaster = redisTemplateMaster;
        this.redisTemplateReadOnly = redisTemplateReadOnly;
    }

    @Override
    public String get(String key) {
        try {
            return redisTemplateReadOnly.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("인증 캐시 조회 실패, DB fallback: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void set(String key, String json, Duration ttl) {
        try {
            redisTemplateMaster.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("인증 캐시 저장 실패: {}", e.getMessage());
        }
    }
}
