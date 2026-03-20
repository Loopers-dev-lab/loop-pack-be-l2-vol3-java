package com.loopers.support.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisCacheHelper {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheHelper(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, typeRef));
        } catch (Exception e) {
            log.warn("Redis GET 실패, DB 폴백: key={}", key, e);
            return Optional.empty();
        }
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Redis SET 실패, 무시: key={}", key, e);
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis DELETE 실패, 무시: key={}", key, e);
        }
    }

    public void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis DELETE 패턴 실패, 무시: pattern={}", pattern, e);
        }
    }
}
