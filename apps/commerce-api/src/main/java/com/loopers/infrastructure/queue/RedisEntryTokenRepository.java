package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class RedisEntryTokenRepository implements EntryTokenRepository {

    private static final String TOKEN_KEY_PREFIX = "entry-token:";
    private static final Duration RESTORE_TTL = Duration.ofMinutes(5);
    private static final DefaultRedisScript<Long> CONSUME_IF_MATCH_SCRIPT;

    static {
        CONSUME_IF_MATCH_SCRIPT = new DefaultRedisScript<>();
        CONSUME_IF_MATCH_SCRIPT.setScriptText(
                "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                "  return redis.call('DEL', KEYS[1]) " +
                "else " +
                "  return 0 " +
                "end"
        );
        CONSUME_IF_MATCH_SCRIPT.setResultType(Long.class);
    }

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> defaultRedisTemplate;

    public RedisEntryTokenRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> defaultRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.defaultRedisTemplate = defaultRedisTemplate;
    }

    @Override
    public void issueToken(Long userId, String token, Duration ttl) {
        masterRedisTemplate.opsForValue()
                .set(TOKEN_KEY_PREFIX + userId, token, ttl);
    }

    @Override
    public Optional<String> getToken(Long userId) {
        String token = defaultRedisTemplate.opsForValue()
                .get(TOKEN_KEY_PREFIX + userId);
        return Optional.ofNullable(token);
    }

    @Override
    public boolean consumeIfMatch(Long userId, String token) {
        Long result = masterRedisTemplate.execute(
                CONSUME_IF_MATCH_SCRIPT,
                List.of(TOKEN_KEY_PREFIX + userId),
                token
        );
        return result != null && result > 0;
    }

    @Override
    public void restoreToken(Long userId, String token) {
        masterRedisTemplate.opsForValue()
                .set(TOKEN_KEY_PREFIX + userId, token, RESTORE_TTL);
    }
}
