package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class WaitingQueueRepositoryImpl implements WaitingQueueRepository {

    private static final String QUEUE_KEY = "waiting-queue";

    private static final String ADD_IF_NOT_FULL_SCRIPT =
        "local size = redis.call('ZCARD', KEYS[1]) " +
        "if size >= tonumber(ARGV[1]) then return -1 end " +
        "return redis.call('ZADD', KEYS[1], 'NX', ARGV[2], ARGV[3])";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean add(Long userId, double score) {
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(QUEUE_KEY, userId.toString(), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Long rank(Long userId) {
        return redisTemplate.opsForZSet().rank(QUEUE_KEY, userId.toString());
    }

    @Override
    public boolean addIfNotFull(Long userId, double score, long maxQueueSize) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ADD_IF_NOT_FULL_SCRIPT, Long.class);
        Long result = redisTemplate.execute(script, List.of(QUEUE_KEY),
            String.valueOf(maxQueueSize), String.valueOf(score), userId.toString());
        return result != null && result >= 0;
    }

    @Override
    public long size() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size != null ? size : 0;
    }

    @Override
    public List<Long> popMin(int count) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            result.add(Long.valueOf(tuple.getValue()));
        }
        return result;
    }
}
