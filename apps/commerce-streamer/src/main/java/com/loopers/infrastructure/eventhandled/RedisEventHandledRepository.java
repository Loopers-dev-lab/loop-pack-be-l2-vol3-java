package com.loopers.infrastructure.eventhandled;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.eventhandled.EventHandledRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis SETNX 기반 이벤트 중복 필터링 구현체.
 *
 * <p>{@code event-handled:{eventId}} 키를 TTL 14일로 저장하여
 * 동일 이벤트의 중복 처리를 방지한다.</p>
 *
 * <p>Redis 장애 시 신규로 간주하여 처리를 계속한다.
 * 지표성 데이터이므로 일시적 중복 적용은 허용 가능하다.</p>
 */
@Slf4j
@Component
public class RedisEventHandledRepository implements EventHandledRepository {

    private static final String KEY_PREFIX = "event-handled:";
    private static final Duration TTL = Duration.ofDays(14);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisEventHandledRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean markIfAbsent(String eventId) {
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + eventId, "1", TTL);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("[EventHandled] Redis 장애로 중복 필터링 skip: eventId={}", eventId, e);
            return true;
        }
    }
}
