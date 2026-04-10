package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WaitingQueueRedisStore implements WaitingQueueRepository {

    static final String QUEUE_KEY = "waiting-queue";

    private final RedisTemplate<String, String> redisTemplate;

    public WaitingQueueRedisStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long enter(Long userId) {
        double score = System.currentTimeMillis();
        redisTemplate.opsForZSet().addIfAbsent(QUEUE_KEY, userId.toString(), score);
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, userId.toString());
        return rank == null ? 1L : rank + 1;
    }

    @Override
    public long getPosition(Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, userId.toString());
        return rank == null ? -1L : rank + 1;
    }

    @Override
    public long getSize() {
        Long size = redisTemplate.opsForZSet().size(QUEUE_KEY);
        return size == null ? 0L : size;
    }

    @Override
    public List<Long> popFront(long count) {
        Set<ZSetOperations.TypedTuple<String>> popped =
            redisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
        if (popped == null || popped.isEmpty()) {
            return Collections.emptyList();
        }
        return popped.stream()
            .map(tuple -> Long.valueOf(tuple.getValue()))
            .toList();
    }
}
