package com.loopers.infrastructure.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenConsumeResult;
import com.loopers.domain.queue.EntryTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.ScanOptions;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class EntryTokenRepositoryImpl implements EntryTokenRepository {

    private static final String TOKEN_KEY_PREFIX = "entry-token:";
    private static final String STATUS_KEY_PREFIX = "queue-status:";
    private static final String STAGING_BATCH_KEY = "staging:batch";
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(EntryToken token, long ttlSeconds) {
        String key = TOKEN_KEY_PREFIX + token.userId();
        String json = serialize(token);
        redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Optional<EntryToken> findByUserId(Long userId) {
        String key = TOKEN_KEY_PREFIX + userId;
        String json = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(json).map(this::deserialize);
    }

    @Override
    public Optional<EntryToken> findAndDeleteByUserId(Long userId) {
        String key = TOKEN_KEY_PREFIX + userId;
        String json = redisTemplate.opsForValue().getAndDelete(key);
        if (json != null) {
            redisTemplate.delete(STATUS_KEY_PREFIX + userId);
            return Optional.of(deserialize(json));
        }
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<EntryTokenConsumeResult> consumeIfActivated(Long userId, long currentTimeMillis) {
        String script = """
            local val = redis.call('GET', KEYS[1])
            if val == false then return nil end
            local data = cjson.decode(val)
            local activateAt = tonumber(data['activateAt'])
            local now = tonumber(ARGV[1])
            if now >= activateAt then
                redis.call('DEL', KEYS[1])
                redis.call('DEL', KEYS[2])
                return {1, val}
            else
                return {0, val}
            end
            """;
        String tokenKey = TOKEN_KEY_PREFIX + userId;
        String statusKey = STATUS_KEY_PREFIX + userId;

        List<Object> result = (List<Object>) redisTemplate.execute(
            new DefaultRedisScript<>(script, List.class),
            List.of(tokenKey, statusKey),
            String.valueOf(currentTimeMillis)
        );

        if (result == null || result.isEmpty() || result.get(0) == null) {
            return Optional.empty();
        }

        boolean consumed = ((Number) result.get(0)).intValue() == 1;
        String json = (String) result.get(1);
        EntryToken token = deserialize(json);
        return Optional.of(new EntryTokenConsumeResult(token, consumed));
    }

    @Override
    public void restore(EntryToken token, long ttlSeconds, long statusTtlSeconds) {
        String key = TOKEN_KEY_PREFIX + token.userId();
        String json = serialize(token);
        redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
        saveStatus(token.userId(), "TOKEN_ISSUED", statusTtlSeconds);
    }

    @Override
    public void saveStatus(Long userId, String status, long ttlSeconds) {
        String key = STATUS_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, status, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Optional<String> getStatus(Long userId) {
        String key = STATUS_KEY_PREFIX + userId;
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public long countActiveTokens() {
        long count = 0;
        var scanOptions = ScanOptions.scanOptions()
            .match(TOKEN_KEY_PREFIX + "*")
            .count(200)
            .build();
        try (var cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        }
        return count;
    }

    @Override
    public Optional<String> acquireLock(String lockKey, long ttlMillis) {
        String value = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, value, ttlMillis, TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(acquired) ? Optional.of(value) : Optional.empty();
    }

    @Override
    public void releaseLock(String lockKey, String lockValue) {
        String script = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """;
        redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
            List.of(lockKey), lockValue);
    }

    @Override
    public void saveStagingBatch(List<Long> userIds) {
        String[] members = userIds.stream().map(String::valueOf).toArray(String[]::new);
        redisTemplate.opsForSet().add(STAGING_BATCH_KEY, members);
    }

    @Override
    public List<Long> getStagingBatch() {
        Set<String> members = redisTemplate.opsForSet().members(STAGING_BATCH_KEY);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().map(Long::valueOf).toList();
    }

    @Override
    public void deleteStagingBatch() {
        redisTemplate.delete(STAGING_BATCH_KEY);
    }

    private String serialize(EntryToken token) {
        try {
            return objectMapper.writeValueAsString(token);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize EntryToken", e);
        }
    }

    private EntryToken deserialize(String json) {
        try {
            return objectMapper.readValue(json, EntryToken.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize EntryToken", e);
        }
    }
}
