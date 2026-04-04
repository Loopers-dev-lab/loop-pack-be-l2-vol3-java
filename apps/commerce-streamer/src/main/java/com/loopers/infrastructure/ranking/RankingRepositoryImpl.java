package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RankingRepositoryImpl implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRepositoryImpl(@Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScore(String key, Long productId, double score, long ttlSeconds) {
        redisTemplate.opsForZSet().incrementScore(key, productId.toString(), score);
        Long expireSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (expireSeconds != null && expireSeconds == -1) {
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
    }
}
