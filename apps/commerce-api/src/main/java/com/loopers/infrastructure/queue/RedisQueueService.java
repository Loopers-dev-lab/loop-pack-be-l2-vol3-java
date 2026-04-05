package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueueService;
import com.loopers.config.redis.RedisConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisQueueService implements QueueService {

    private static final String QUEUE_KEY = "order:waiting-queue";
    private static final String QUEUE_SEQUENCE_KEY = "order:waiting-queue:sequence";
    private static final String QUEUE_ENTER_LOCK_KEY = "order:waiting-queue:lock";
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;

    public RedisQueueService(
            RedissonClient redissonClient,
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) StringRedisTemplate redisTemplate
    ) {
        this.redissonClient = redissonClient;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long enter(Long userId) {
        RLock lock = redissonClient.getLock(QUEUE_ENTER_LOCK_KEY);
        lock.lock();
        try {
            String member = String.valueOf(userId);
            Long existingRank = redisTemplate.opsForZSet().rank(QUEUE_KEY, member);
            if (existingRank != null) {
                return existingRank;
            }

            Long sequence = redisTemplate.opsForValue().increment(QUEUE_SEQUENCE_KEY);
            if (sequence == null) {
                throw new CoreException(ErrorType.INTERNAL_ERROR);
            }
            redisTemplate.opsForZSet().add(QUEUE_KEY, member, sequence.doubleValue());

            Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, member);
            if (rank == null || rank < 0) {
                throw new CoreException(ErrorType.QUEUE_NOT_FOUND);
            }
            return rank;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public long getRank(Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId));
        if (rank == null) {
            throw new CoreException(ErrorType.QUEUE_NOT_FOUND);
        }
        return rank;
    }

    @Override
    public long getSize() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size == null ? 0L : size;
    }

    @Override
    public List<Long> peekBatch(int count) {
        if (count <= 0) {
            return List.of();
        }
        var values = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, count - 1);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(Long::parseLong)
                .toList();
    }

    @Override
    public void remove(Long userId) {
        redisTemplate.opsForZSet().remove(QUEUE_KEY, String.valueOf(userId));
    }
}
