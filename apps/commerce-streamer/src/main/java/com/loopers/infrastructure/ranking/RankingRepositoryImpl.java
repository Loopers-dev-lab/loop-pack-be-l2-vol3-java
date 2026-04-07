package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Repository
@Slf4j
public class RankingRepositoryImpl implements RankingRepository {

    private static final String RANKING_KEY_PREFIX = "ranking:all:";
    private static final String LIKED_KEY_PREFIX = "ranking:liked:";
    private static final Duration TTL = Duration.ofDays(2);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final RedisTemplate<String, String> redisTemplateMaster;

    public RankingRepositoryImpl(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplateMaster) {
        this.redisTemplateMaster = redisTemplateMaster;
    }

    @Override
    public void incrementScore(Long productId, double score, LocalDate date) {
        String key = RANKING_KEY_PREFIX + date.format(DATE_FORMAT);
        String member = String.valueOf(productId);

        redisTemplateMaster.opsForZSet().incrementScore(key, member, score);
        redisTemplateMaster.expire(key, TTL);
    }

    @Override
    public boolean addLikeIfAbsent(Long productId, Long userId, LocalDate date) {
        String key = LIKED_KEY_PREFIX + date.format(DATE_FORMAT);
        String member = productId + ":" + userId;

        Long result = redisTemplateMaster.opsForSet().add(key, member);
        redisTemplateMaster.expire(key, TTL);
        return result != null && result > 0;
    }

    @Override
    public boolean removeLikeIfPresent(Long productId, Long userId, LocalDate date) {
        String key = LIKED_KEY_PREFIX + date.format(DATE_FORMAT);
        String member = productId + ":" + userId;

        Long result = redisTemplateMaster.opsForSet().remove(key, member);
        return result != null && result > 0;
    }
}
