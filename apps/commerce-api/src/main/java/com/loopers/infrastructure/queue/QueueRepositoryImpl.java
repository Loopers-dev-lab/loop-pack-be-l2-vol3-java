package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueConstants;
import com.loopers.domain.queue.QueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class QueueRepositoryImpl implements QueueRepository {

    private final StringRedisTemplate redisTemplate;

    @Override
    public long enter(String userId, long score) {
        redisTemplate.opsForZSet().addIfAbsent(QueueConstants.QUEUE_KEY, userId, score);
        Long rank = redisTemplate.opsForZSet().rank(QueueConstants.QUEUE_KEY, userId);
        return rank + 1;
    }

    @Override
    public Optional<Long> findPosition(String userId) {
        Long rank = redisTemplate.opsForZSet().rank(QueueConstants.QUEUE_KEY, userId);
        if (rank == null) {
            return Optional.empty();
        }
        return Optional.of(rank + 1);
    }

    @Override
    public long getTotalCount() {
        Long count = redisTemplate.opsForZSet().size(QueueConstants.QUEUE_KEY);
        return count != null ? count : 0L;
    }

    @Override
    public void savePresence(String userId) {
        redisTemplate.opsForValue().set(
                QueueConstants.PRESENCE_KEY_PREFIX + userId, "1",
                QueueConstants.PRESENCE_TTL_SECONDS, TimeUnit.SECONDS
        );
    }

    @Override
    public void refreshPresence(String userId) {
        redisTemplate.expire(
                QueueConstants.PRESENCE_KEY_PREFIX + userId,
                QueueConstants.PRESENCE_TTL_SECONDS, TimeUnit.SECONDS
        );
    }
}