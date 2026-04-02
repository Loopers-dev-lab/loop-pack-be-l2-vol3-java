package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class QueueRepositoryImpl implements QueueRepository {

    private static final String WAITING_KEY = "queue:waiting";
    private static final String TOKEN_KEY_PREFIX = "queue:token:";

    private static final DefaultRedisScript<List> MOVE_TO_ACTIVE_SCRIPT;

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
    }

    private final RedisTemplate<String, String> redisTemplate;

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
}
