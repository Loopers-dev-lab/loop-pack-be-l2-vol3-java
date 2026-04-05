package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class QueueRepositoryImpl implements QueueRepository {

    private static final String QUEUE_KEY = "queue:waiting";

    private final RedisTemplate<String, String> redisTemplate;

    public QueueRepositoryImpl(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean enter(Long userId, double score) {
        Boolean added = redisTemplate.opsForZSet()
                .addIfAbsent(QUEUE_KEY, String.valueOf(userId), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Long getPosition(Long userId) {
        return redisTemplate.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId));
    }

    @Override
    public long getTotalSize() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size != null ? size : 0;
    }

    @Override
    public Set<String> pollBatch(int count) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .popMin(QUEUE_KEY, count);

        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptySet();
        }

        return tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
