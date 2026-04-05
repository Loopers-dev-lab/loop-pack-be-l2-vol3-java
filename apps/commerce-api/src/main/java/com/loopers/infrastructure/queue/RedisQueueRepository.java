package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class RedisQueueRepository implements QueueRepository {

    private static final String WAIT_QUEUE_KEY = "queue:wait";
    private static final String TOKEN_KEY_PREFIX = "queue:token:";
    private static final String ACTIVE_TOKEN_COUNT_KEY = "queue:active-token-count";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean enterWaitQueue(Long userId, double score) {
        redisTemplate.opsForZSet().add(WAIT_QUEUE_KEY, userId.toString(), score);
        return true;
    }

    @Override
    public Long getWaitPosition(Long userId) {
        return redisTemplate.opsForZSet().rank(WAIT_QUEUE_KEY, userId.toString());
    }

    @Override
    public Long getWaitTotalCount() {
        Long count = redisTemplate.opsForZSet().zCard(WAIT_QUEUE_KEY);
        return count != null ? count : 0L;
    }

    @Override
    public List<Long> popFromWaitQueue(long count) {
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(WAIT_QUEUE_KEY, count);
        if (popped == null || popped.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : popped) {
            userIds.add(Long.parseLong(tuple.getValue()));
        }
        return userIds;
    }

    @Override
    public void issueToken(Long userId, String token, long ttlSeconds) {
        redisTemplate.opsForValue().set(tokenKey(userId), token, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public boolean hasActiveToken(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey(userId)));
    }

    @Override
    public String getToken(Long userId) {
        return redisTemplate.opsForValue().get(tokenKey(userId));
    }

    @Override
    public void removeToken(Long userId) {
        redisTemplate.delete(tokenKey(userId));
    }

    @Override
    public long getActiveTokenCount() {
        String value = redisTemplate.opsForValue().get(ACTIVE_TOKEN_COUNT_KEY);
        if (value == null) {
            return 0L;
        }
        long count = Long.parseLong(value);
        return Math.max(count, 0L);
    }

    @Override
    public void incrementActiveTokenCount(long count, long ttlSeconds) {
        Long result = redisTemplate.opsForValue().increment(ACTIVE_TOKEN_COUNT_KEY, count);
        if (result != null && result == count) {
            redisTemplate.expire(ACTIVE_TOKEN_COUNT_KEY, Duration.ofSeconds(ttlSeconds));
        }
    }

    @Override
    public void decrementActiveTokenCount() {
        redisTemplate.opsForValue().increment(ACTIVE_TOKEN_COUNT_KEY, -1);
    }

    private String tokenKey(Long userId) {
        return TOKEN_KEY_PREFIX + userId;
    }
}
