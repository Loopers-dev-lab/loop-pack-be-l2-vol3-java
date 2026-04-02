package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueueService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisQueueService implements QueueService {

    private static final String QUEUE_KEY = "order:waiting-queue";

    private final StringRedisTemplate redisTemplateMaster;

    @Override
    public long enter(Long userId) {
        redisTemplateMaster.opsForZSet().addIfAbsent(QUEUE_KEY, String.valueOf(userId), System.currentTimeMillis());
        return getRank(userId);
    }

    @Override
    public long getRank(Long userId) {
        Long rank = redisTemplateMaster.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId));
        if (rank == null) {
            throw new CoreException(ErrorType.QUEUE_NOT_FOUND);
        }
        return rank;
    }

    @Override
    public long getSize() {
        Long size = redisTemplateMaster.opsForZSet().size(QUEUE_KEY);
        return size == null ? 0L : size;
    }

    @Override
    public List<Long> peekBatch(int count) {
        Set<String> values = redisTemplateMaster.opsForZSet().range(QUEUE_KEY, 0, count - 1);
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(Long::parseLong)
                .toList();
    }

    @Override
    public void remove(Long userId) {
        redisTemplateMaster.opsForZSet().remove(QUEUE_KEY, String.valueOf(userId));
    }
}
