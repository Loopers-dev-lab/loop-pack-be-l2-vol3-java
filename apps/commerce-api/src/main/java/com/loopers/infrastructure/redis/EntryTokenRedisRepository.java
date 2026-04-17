package com.loopers.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 입장 토큰 Redis 저장소.
 *
 * <p>대기열 통과 시 발급되는 토큰. TTL 내에 주문을 완료해야 한다.</p>
 *
 * <p>TTL 산술 근거 (기본 900초 = 15분):</p>
 * <ul>
 *   <li>체크아웃 p99 예상 시간 ~10분 + 50% 여유</li>
 *   <li>동시 토큰 보유자 = 80 TPS × 234초(가중 평균 체류) = 18,720명</li>
 *   <li>Redis 메모리: 18,720 × 90 bytes = 1.7MB (무시 가능)</li>
 *   <li>블프 시 설정 변경으로 1800초까지 조정 가능</li>
 * </ul>
 */
@Slf4j
@Component
public class EntryTokenRedisRepository {

    private static final String KEY_PREFIX = "queue:token:";

    private final long tokenTtlSeconds;
    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;

    public EntryTokenRedisRepository(
        @Value("${queue.token.ttl-seconds:900}") long tokenTtlSeconds,
        RedisTemplate<String, String> readTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate
    ) {
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
    }

    /**
     * 토큰 발급 (SET EX {ttl}).
     */
    public void issue(Long memberId) {
        String key = KEY_PREFIX + memberId;
        writeTemplate.opsForValue().set(key, "1", tokenTtlSeconds, TimeUnit.SECONDS);
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
