package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String QUEUE_KEY = "waiting-queue";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> defaultRedisTemplate;

    public RedisWaitingQueueRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> defaultRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.defaultRedisTemplate = defaultRedisTemplate;
    }

    @Override
    public boolean enqueue(Long userId, double score) {
        Boolean added = masterRedisTemplate.opsForZSet()
                                           .add(QUEUE_KEY, String.valueOf(userId), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Long getRank(Long userId) {
        return defaultRedisTemplate.opsForZSet()
                                   .rank(QUEUE_KEY, String.valueOf(userId));
    }

    @Override
    public long getTotalCount() {
        Long size = defaultRedisTemplate.opsForZSet().size(QUEUE_KEY);
        return size != null ? size : 0L;
    }

    @Override
    public Set<Long> dequeue(int count) {
        Set<ZSetOperations.TypedTuple<String>> popped = masterRedisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
        if (popped == null) return Set.of();

        return popped.stream()
                     .map(tuple -> Long.parseLong(tuple.getValue()))
                     .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
