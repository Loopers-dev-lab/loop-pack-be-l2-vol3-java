package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class WaitingQueueRedisRepository implements WaitingQueueRepository {

    private static final String KEY = "queue:waiting";

    private final RedisTemplate<String, String> redisTemplate;

    public WaitingQueueRedisRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean enter(Long memberId, double score) {
        Boolean added = redisTemplate.opsForZSet()
                .addIfAbsent(KEY, String.valueOf(memberId), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> getPosition(Long memberId) {
        Long rank = redisTemplate.opsForZSet().rank(KEY, String.valueOf(memberId));
        if (rank == null) {
            return Optional.empty();
        }
        return Optional.of(rank + 1);
    }

    @Override
    public long getTotalCount() {
        Long size = redisTemplate.opsForZSet().zCard(KEY);
        return size != null ? size : 0L;
    }

    @Override
    public List<Long> popN(int count) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().popMin(KEY, count);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<Long> memberIds = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            memberIds.add(Long.parseLong(tuple.getValue()));
        }
        return memberIds;
    }

    @Override
    public List<Map.Entry<Long, Double>> popNWithScore(int count) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().popMin(KEY, count);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<Map.Entry<Long, Double>> result = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            result.add(new AbstractMap.SimpleEntry<>(
                    Long.parseLong(tuple.getValue()),
                    tuple.getScore()
            ));
        }
        return result;
    }

    @Override
    public void clear() {
        redisTemplate.delete(KEY);
    }
}
