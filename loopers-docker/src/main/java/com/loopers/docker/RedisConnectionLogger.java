package com.loopers.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisConnectionLogger {

    private static final Logger log = LoggerFactory.getLogger(RedisConnectionLogger.class);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisConnectionLogger(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConnectionStatus() {
        try {
            // Redis에 명령을 날려서 container 로그에 기록 남김
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            log.info("✅ Redis Connection SUCCESS: {}", ping);

            // 테스트 데이터 쓰기/읽기
            String key = "spring-boot:connection-test";
            String value = "Connected at " + System.currentTimeMillis();
            redisTemplate.opsForValue().set(key, value);
            String result = redisTemplate.opsForValue().get(key);
            log.info("   └─ Redis Test Write/Read: {}", result);

            // 정리
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("❌ Redis Connection FAILED", e);
        }
    }
}
