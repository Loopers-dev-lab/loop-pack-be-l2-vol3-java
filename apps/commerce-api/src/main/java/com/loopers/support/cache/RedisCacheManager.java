package com.loopers.support.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisCacheManager {

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheManager(
            RedisTemplate<String, String> readTemplate,
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate,
            ObjectMapper objectMapper
    ) {
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = readTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Redis GET 실패 - key: {}", key, e);
            return Optional.empty();
        }
    }

    public void put(String key, Object value, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            writeTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis SET 실패 - key: {}", key, e);
        }
    }

    public void evict(String key) {
        try {
            writeTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis DEL 실패 - key: {}", key, e);
        }
    }

    public Long increment(String key) {
        try {
            return writeTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.warn("Redis INCR 실패 - key: {}", key, e);
            return null;
        }
    }

    public Long decrement(String key) {
        try {
            return writeTemplate.opsForValue().decrement(key);
        } catch (Exception e) {
            log.warn("Redis DECR 실패 - key: {}", key, e);
            return null;
        }
    }

    public Optional<Long> getCount(String key) {
        try {
            String value = readTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(value));
        } catch (Exception e) {
            log.warn("Redis GET count 실패 - key: {}", key, e);
            return Optional.empty();
        }
    }

    public void setCount(String key, long value, long ttlSeconds) {
        try {
            writeTemplate.opsForValue().set(key, String.valueOf(value), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis SET count 실패 - key: {}", key, e);
        }
    }
}
