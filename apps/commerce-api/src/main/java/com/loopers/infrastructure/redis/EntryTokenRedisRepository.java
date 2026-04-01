package com.loopers.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 입장 토큰 Redis 저장소.
 *
 * <p>대기열 통과 시 발급되는 토큰. TTL 300초(5분) 내에 주문을 완료해야 한다.</p>
 */
@Slf4j
@Component
public class EntryTokenRedisRepository {

    private static final String KEY_PREFIX = "queue:token:";
    private static final long TOKEN_TTL_SECONDS = 300;

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;

    public EntryTokenRedisRepository(
        RedisTemplate<String, String> readTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate
    ) {
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
    }

    /**
     * 토큰 발급 (SET EX 300).
     */
    public void issue(Long memberId) {
        String key = KEY_PREFIX + memberId;
        writeTemplate.opsForValue().set(key, "1", TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("입장 토큰 발급: memberId={}", memberId);
    }

    /**
     * 토큰 존재 확인 (replica 읽기).
     */
    public boolean exists(Long memberId) {
        String key = KEY_PREFIX + memberId;
        return Boolean.TRUE.equals(readTemplate.hasKey(key));
    }

    /**
     * 토큰 소비 (DEL).
     */
    public void consume(Long memberId) {
        String key = KEY_PREFIX + memberId;
        writeTemplate.delete(key);
        log.debug("입장 토큰 소비: memberId={}", memberId);
    }

    /**
     * 잔여 TTL 조회 (초 단위).
     *
     * @return TTL (키 없으면 -2, TTL 없으면 -1)
     */
    public long getRemainingTtl(Long memberId) {
        String key = KEY_PREFIX + memberId;
        Long ttl = readTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -2;
    }
}
