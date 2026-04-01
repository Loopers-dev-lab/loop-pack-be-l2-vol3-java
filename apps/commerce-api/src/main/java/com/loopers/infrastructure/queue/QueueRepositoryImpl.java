package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class QueueRepositoryImpl implements QueueRepository {

    private static final String QUEUE_KEY = "queue:waiting";

    private final StringRedisTemplate redisTemplate;

    @Override
    public long enter(String userId, long score) {
        redisTemplate.opsForZSet().addIfAbsent(QUEUE_KEY, userId, score);
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, userId);
        return rank + 1;
    }

    @Override
    public Optional<Long> findPosition(String userId) {
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, userId);
        if (rank == null) {
            return Optional.empty();
        }
        return Optional.of(rank + 1);
    }

    @Override
    public long getTotalCount() {
        Long count = redisTemplate.opsForZSet().size(QUEUE_KEY);
        return count != null ? count : 0L;
    }
}