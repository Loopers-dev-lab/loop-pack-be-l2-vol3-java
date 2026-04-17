package com.loopers.infrastructure.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RankingZSetRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public void rebuildZSet(String key, Map<Long, Double> scores, Duration ttl) {
        if (scores.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }

        String shadowKey = key + ":rebuild";

        redisTemplate.delete(shadowKey);

        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> tuples = scores.entrySet().stream()
                .map(e -> ZSetOperations.TypedTuple.of(
                        String.valueOf(e.getKey()),
                        e.getValue()
                ))
                .collect(java.util.stream.Collectors.toSet());

        zSet.add(shadowKey, tuples);
        redisTemplate.rename(shadowKey, key);
        redisTemplate.expire(key, ttl);
    }
}
