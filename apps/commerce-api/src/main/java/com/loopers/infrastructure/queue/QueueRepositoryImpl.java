package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class QueueRepositoryImpl implements QueueRepository {

    private static final String WAITING_KEY = "queue:waiting";
    private static final String TOKEN_KEY_PREFIX = "queue:token:";

    private static final DefaultRedisScript<List> MOVE_TO_ACTIVE_SCRIPT;
    private static final DefaultRedisScript<Long> EXTEND_TOKEN_IF_NEAR_EXPIRY_SCRIPT;

    static {
        MOVE_TO_ACTIVE_SCRIPT = new DefaultRedisScript<>();
        MOVE_TO_ACTIVE_SCRIPT.setScriptText("""
                local members = redis.call('ZRANGE', KEYS[1], 0, ARGV[1] - 1)
                if #members == 0 then return {} end
                local ttl = tonumber(ARGV[2])
                for i, userId in ipairs(members) do
                    redis.call('SET', 'queue:token:' .. userId, ARGV[i + 2], 'EX', ttl)
                end
                redis.call('ZREM', KEYS[1], unpack(members))
                return members
                """);
        MOVE_TO_ACTIVE_SCRIPT.setResultType(List.class);

        EXTEND_TOKEN_IF_NEAR_EXPIRY_SCRIPT = new DefaultRedisScript<>();
        EXTEND_TOKEN_IF_NEAR_EXPIRY_SCRIPT.setScriptText("""
                local ttl = redis.call('TTL', KEYS[1])
                if ttl > 0 and ttl < tonumber(ARGV[1]) then
                    redis.call('EXPIRE', KEYS[1], ttl + tonumber(ARGV[2]))
                    return 1
                end
                return 0
                """);
        EXTEND_TOKEN_IF_NEAR_EXPIRY_SCRIPT.setResultType(Long.class);
    }

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, String> masterRedisTemplate;

    public QueueRepositoryImpl(
            RedisTemplate<String, String> redisTemplate,
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public void enter(long userId, double score) {
        redisTemplate.opsForZSet().add(WAITING_KEY, String.valueOf(userId), score);
    }

    @Override
    public boolean isInWaiting(long userId) {
        return redisTemplate.opsForZSet().score(WAITING_KEY, String.valueOf(userId)) != null;
    }

    @Override
    public Optional<Long> getRank(long userId) {
        Long rank = redisTemplate.opsForZSet().rank(WAITING_KEY, String.valueOf(userId));
        return Optional.ofNullable(rank);
    }

    @Override
    public Optional<String> findToken(long userId) {
        String token = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId);
        return Optional.ofNullable(token);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> issueTokens(int count, long ttlSeconds, List<String> uuids) {
        List<String> argv = new ArrayList<>();
        argv.add(String.valueOf(count));
        argv.add(String.valueOf(ttlSeconds));
        argv.addAll(uuids);

        List<String> result = redisTemplate.execute(
                MOVE_TO_ACTIVE_SCRIPT,
                List.of(WAITING_KEY),
                argv.toArray(String[]::new)
        );
        if (result == null || result.isEmpty()) {
            return List.of();
        }
        return result.stream()
                .map(Long::valueOf)
                .toList();
    }

    @Override
    public void removeToken(long userId) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + userId);
    }

    @Override
    public boolean extendTokenIfNearExpiry(long userId, long thresholdSeconds, long additionalSeconds) {
        String key = TOKEN_KEY_PREFIX + userId;
        Long result = masterRedisTemplate.execute(
                EXTEND_TOKEN_IF_NEAR_EXPIRY_SCRIPT,
                List.of(key),
                String.valueOf(thresholdSeconds),
                String.valueOf(additionalSeconds)
        );
        return Long.valueOf(1L).equals(result);
    }
}
