package com.loopers.application.order.queue;

import com.loopers.config.redis.RedisConfig;
import io.lettuce.core.RedisAsyncCommandsImpl;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "loopers.queue.order", name = "admission-strategy", havingValue = "hash-field-ttl")
public class HashFieldTtlAdmissionStorageStrategy implements AdmissionStorageStrategy {

    private static final String CLAIM_BUCKET_KEY = "order:admission:claim-bucket:v1";
    private static final String TOKEN_BUCKET_KEY = "order:admission:token-bucket:v1";

    private final RedisTemplate<String, String> redisTemplate;

    public HashFieldTtlAdmissionStorageStrategy(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryClaim(String memberId, long nowMillis, long claimTtlMillis) {
        return putWithFieldTtl(CLAIM_BUCKET_KEY, memberId, String.valueOf(nowMillis), claimTtlMillis);
    }

    @Override
    public boolean hasValidToken(String memberId) {
        return hasField(TOKEN_BUCKET_KEY, memberId);
    }

    @Override
    public boolean hasActiveClaim(String memberId) {
        return hasField(CLAIM_BUCKET_KEY, memberId);
    }

    @Override
    public boolean issueToken(String memberId, long nowMillis, long tokenTtlMillis) {
        return putWithFieldTtl(TOKEN_BUCKET_KEY, memberId, String.valueOf(nowMillis + tokenTtlMillis), tokenTtlMillis);
    }

    @Override
    public long countActiveTokens(long nowMillis) {
        Long size = redisTemplate.opsForHash().size(TOKEN_BUCKET_KEY);
        return size == null ? 0L : size;
    }

    @Override
    public void removeToken(String memberId) {
        redisTemplate.opsForHash().delete(TOKEN_BUCKET_KEY, memberId);
    }

    @Override
    public void clearClaim(String memberId) {
        Long deleted = redisTemplate.opsForHash().delete(CLAIM_BUCKET_KEY, memberId);
        log.info("hash-field-ttl clearClaim memberId={} deleted={}", memberId, deleted);
    }

    private boolean hasField(String key, String field) {
        Boolean exists = redisTemplate.opsForHash().hasKey(key, field);
        return Boolean.TRUE.equals(exists);
    }

    private boolean putWithFieldTtl(String key, String field, String value, long ttlMillis) {
        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
        byte[] serializedKey = serializer.serialize(key);
        byte[] serializedField = serializer.serialize(field);
        byte[] serializedValue = serializer.serialize(value);
        Object result = redisTemplate.execute((RedisCallback<Object>) connection -> connection.execute(
                "HSETNX",
                serializedKey,
                serializedField,
                serializedValue
        ));
        log.info("hash-field-ttl put key={} field={} hsetnxResult={}", key, field, result);
        if (toLong(result) != 1L) {
            return false;
        }

        try {
            Object expireResult = redisTemplate.execute((RedisCallback<Object>) connection -> {
                Object nativeConnection = connection.getNativeConnection();
                log.info("hash-field-ttl nativeConnectionClass={}", nativeConnection == null ? "null" : nativeConnection.getClass().getName());
                @SuppressWarnings("unchecked")
                StatefulRedisConnection<byte[], byte[]> statefulConnection = nativeConnection instanceof RedisAsyncCommandsImpl<?, ?> asyncCommands
                        ? ((RedisAsyncCommandsImpl<byte[], byte[]>) asyncCommands).getStatefulConnection()
                        : (StatefulRedisConnection<byte[], byte[]>) nativeConnection;
                return statefulConnection.sync().hpexpire(serializedKey, ttlMillis, serializedField);
            });
            log.info("hash-field-ttl expire key={} field={} expireResult={}", key, field, expireResult);
            return true;
        } catch (RuntimeException e) {
            log.error("hash-field-ttl expire failed key={} field={}", key, field, e);
            throw e;
        }
    }

    private long toLong(Object result) {
        if (result == null) {
            return 0L;
        }
        if (result instanceof Boolean booleanValue) {
            return booleanValue ? 1L : 0L;
        }
        if (result instanceof Long longValue) {
            return longValue;
        }
        if (result instanceof Integer intValue) {
            return intValue.longValue();
        }
        if (result instanceof byte[] bytes) {
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        }
        return Long.parseLong(result.toString());
    }
}
