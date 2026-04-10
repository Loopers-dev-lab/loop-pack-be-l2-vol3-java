package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

@Component
public class EntryTokenRedisStore implements EntryTokenRepository {

    static final String KEY_PREFIX = "entry-token:";

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    public EntryTokenRedisStore(
            RedisTemplate<String, String> redisTemplate,
            @Value("${entry-token.ttl-seconds}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public void save(EntryToken token) {
        redisTemplate.opsForValue().set(key(token.getUserId()), token.getToken(), ttl);
    }

    @Override
    public Optional<EntryToken> findByUserId(Long userId) {
        String token = redisTemplate.opsForValue().get(key(userId));
        if (token == null) {
            return Optional.empty();
        }
        return Optional.of(EntryToken.of(userId, token));
    }

    @Override
    public void deleteByUserId(Long userId) {
        redisTemplate.delete(key(userId));
    }

    @Override
    public long countActive() {
        Long count = redisTemplate.execute((RedisCallback<Long>) connection -> {
            long result = 0L;
            ScanOptions options = ScanOptions.scanOptions()
                .match(KEY_PREFIX + "*")
                .count(100)
                .build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    cursor.next();
                    result++;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to count active tokens", e);
            }
            return result;
        });
        return count != null ? count : 0L;
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
