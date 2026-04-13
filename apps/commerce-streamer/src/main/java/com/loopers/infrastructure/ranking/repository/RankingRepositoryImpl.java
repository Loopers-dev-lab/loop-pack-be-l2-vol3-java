package com.loopers.infrastructure.ranking.repository;

import com.loopers.domain.ranking.repository.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class RankingRepositoryImpl implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRepositoryImpl(@Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void updateScore(String key, String member, double score) {
        redisTemplate.opsForZSet().add(key, member, score);
    }

    @Override
    public void setKeyExpire(String key, long ttlSeconds) {
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void carryOverScores(String fromKey, String toKey, double weight) {
        redisTemplate.opsForZSet().unionAndStore(
                fromKey,
                Collections.emptyList(),
                toKey,
                Aggregate.SUM,
                Weights.of(weight)
        );
    }

    @Override
    public boolean existsKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
