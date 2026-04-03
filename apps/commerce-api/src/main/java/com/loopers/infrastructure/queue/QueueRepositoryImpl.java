package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class QueueRepositoryImpl implements QueueRepository {

    private static final String KEY_PREFIX = "queue:";
    private static final String KEY_SUFFIX = ":waiting";

    private final RedisTemplate<String, String> redisTemplate;

    public QueueRepositoryImpl(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean add(String eventId, Long userId) {
        String key = generateKey(eventId);
        Boolean added = redisTemplate.opsForZSet()
                .addIfAbsent(key, String.valueOf(userId), System.currentTimeMillis());
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> getPosition(String eventId, Long userId) {
        String key = generateKey(eventId);
        Long rank = redisTemplate.opsForZSet().rank(key, String.valueOf(userId));
        return Optional.ofNullable(rank);
    }

    @Override
    public long getTotalCount(String eventId) {
        String key = generateKey(eventId);
        Long size = redisTemplate.opsForZSet().zCard(key);
        return size != null ? size : 0L;
    }

    @Override
    public List<Long> popFront(String eventId, int count) {
        String key = generateKey(eventId);
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, count);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }
        return tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .map(Long::parseLong)
                .toList();
    }

    private String generateKey(String eventId) {
        return KEY_PREFIX + eventId + KEY_SUFFIX;
    }
}
