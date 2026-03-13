package com.loopers.infrastructure.shared.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.shared.cache.CacheRepository;
import com.loopers.domain.shared.cache.CacheType;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 기반 {@link CacheRepository} 구현체.
 *
 * <p>값은 JSON 문자열로 직렬화하여 Redis에 저장하며,
 * 역직렬화 시 {@link CacheType}의 타입 정보를 활용한다.</p>
 *
 * @see CacheRepository
 * @see CacheType
 */
@Slf4j
@Repository
public class RedisCacheRepository implements CacheRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 주입받은 ObjectMapper를 복사하여 캐시 전용으로 구성한다.
     *
     * <p>글로벌 ObjectMapper의 설정을 오염시키지 않기 위해 {@code copy()}로 별도 인스턴스를 생성하며,
     * getter/setter 없이 필드 직접 접근으로 직렬화하도록 visibility를 재설정한다. 이는 Lombok {@code @Getter}만 사용하고 setter가 없는 Entity/VO를 안전하게 처리하기
     * 위함이다.</p>
     */
    public RedisCacheRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy()
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    @Override
    public <T> void put(String key, T value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json);
            log.debug("Cache PUT — key={}", key);
        } catch (JsonProcessingException e) {
            log.warn("캐시 직렬화 실패, key={}", key, e);
        }
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
            log.debug("Cache PUT — key={}, ttl={}", key, ttl);
        } catch (JsonProcessingException e) {
            log.warn("캐시 직렬화 실패, key={}", key, e);
        }
    }

    @Override
    public <T> T get(String key, CacheType<T> type) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            log.debug("Cache MISS — key={}", key);
            return null;
        }
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructType(type.getType());
            T result = objectMapper.readValue(json, javaType);
            log.debug("Cache HIT — key={}", key);
            return result;
        } catch (JsonProcessingException e) {
            log.warn("캐시 역직렬화 실패, key={}", key, e);
            redisTemplate.delete(key);
            return null;
        }
    }

    @Override
    public <T> List<T> multiGet(List<String> keys, CacheType<T> type) {
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> jsonList = redisTemplate.opsForValue().multiGet(keys);
        if (jsonList == null) {
            return keys.stream().map(k -> (T) null).toList();
        }
        JavaType javaType = objectMapper.getTypeFactory().constructType(type.getType());
        return jsonList.stream()
                .map(json -> this.<T>deserializeOrNull(json, javaType))
                .toList();
    }

    @Override
    public <T> void multiPut(Map<String, T> entries, Supplier<Duration> ttlSupplier) {
        if (entries.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined((RedisConnection connection) -> {
            entries.forEach((key, value) -> {
                try {
                    String json = objectMapper.writeValueAsString(value);
                    connection.stringCommands().setEx(
                            key.getBytes(StandardCharsets.UTF_8), ttlSupplier.get().getSeconds(), json.getBytes(StandardCharsets.UTF_8)
                    );
                } catch (JsonProcessingException e) {
                    log.warn("캐시 직렬화 실패, key={}", key, e);
                }
            });
            return null;
        });
        log.debug("Cache MULTI_PUT — keys={}", entries.keySet());
    }

    @Override
    public void evict(String keyPattern) {
        if (!keyPattern.contains("*")) {
            redisTemplate.delete(keyPattern);
            log.debug("Cache EVICT — key={}", keyPattern);
            return;
        }

        int batchSize = 100;
        ScanOptions options = ScanOptions.scanOptions().match(keyPattern).count(batchSize).build();
        long totalDeleted = 0;

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            List<String> batch = new ArrayList<>(batchSize);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= batchSize) {
                    redisTemplate.delete(batch);
                    totalDeleted += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
                totalDeleted += batch.size();
            }
        }

        if (totalDeleted > 0) {
            log.debug("Cache EVICT — pattern={}, deletedKeys={}", keyPattern, totalDeleted);
        }
    }

    private <T> T deserializeOrNull(String json, JavaType javaType) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            log.warn("캐시 역직렬화 실패", e);
            return null;
        }
    }
}
