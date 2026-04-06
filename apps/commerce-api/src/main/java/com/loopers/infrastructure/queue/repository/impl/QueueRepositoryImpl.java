package com.loopers.infrastructure.queue.repository.impl;

import com.loopers.domain.queue.repository.QueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class QueueRepositoryImpl implements QueueRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String QUEUE_KEY = "waiting-queue";
    private static final String SEQ_KEY = "queue:seq";
    private static final String TOKEN_KEY_PREFIX = "entry-token:";
    private static final String ACTIVE_TOKEN_COUNT_KEY = "active-token-count";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ENTER_QUEUE_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local seqKey = KEYS[2]
            local memberId = ARGV[1]

            if redis.call('zscore', key, memberId) then
                local rank = redis.call('zrank', key, memberId)
                local total = redis.call('zcard', key)
                return {rank, total, 0}
            end

            local seq = redis.call('incr', seqKey)
            redis.call('zadd', key, seq, memberId)
            local rank = redis.call('zrank', key, memberId)
            local total = redis.call('zcard', key)
            return {rank, total, 1}
            """, List.class);

    @Override
    public Long generateSequence() {
        return redisTemplate.opsForValue().increment(SEQ_KEY);
    }

    @Override
    public boolean addToQueue(Long memberId, long score) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForZSet().add(QUEUE_KEY, memberId.toString(), score)
        );
    }

    @Override
    public Long getRank(Long memberId) {
        return redisTemplate.opsForZSet().rank(QUEUE_KEY, memberId.toString());
    }

    @Override
    public Long getTotalWaiting() {
        return redisTemplate.opsForZSet().zCard(QUEUE_KEY);
    }

    @Override
    public Double getScore(Long memberId) {
        return redisTemplate.opsForZSet().score(QUEUE_KEY, memberId.toString());
    }

    @Override
    public void removeFromQueue(Long memberId) {
        redisTemplate.opsForZSet().remove(QUEUE_KEY, memberId.toString());
    }

    @Override
    public Set<String> getTopMembers(int count) {
        return redisTemplate.opsForZSet().range(QUEUE_KEY, 0, count - 1);
    }

    @Override
    public Set<String> getMembers(long start, long end) {
        return redisTemplate.opsForZSet().range(QUEUE_KEY, start, end);
    }

    @Override
    public void setToken(Long memberId, String token, long ttlSeconds) {
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + memberId, token, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String getToken(Long memberId) {
        return redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + memberId);
    }

    @Override
    public void deleteToken(Long memberId) {
        redisTemplate.delete(TOKEN_KEY_PREFIX + memberId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public QueueEntryResult enterQueue(Long memberId) {
        List<Long> result = redisTemplate.execute(
                ENTER_QUEUE_SCRIPT,
                List.of(QUEUE_KEY, SEQ_KEY),
                memberId.toString()
        );
        if (result == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "대기열 진입 처리 중 오류가 발생했습니다.");
        }
        long rank = result.get(0);
        long total = result.get(1);
        boolean isNew = result.get(2) == 1;
        return new QueueEntryResult(rank, total, isNew);
    }

    @Override
    public boolean refreshTokenTtl(Long memberId, long ttlSeconds) {
        return Boolean.TRUE.equals(
                redisTemplate.expire(TOKEN_KEY_PREFIX + memberId, ttlSeconds, TimeUnit.SECONDS)
        );
    }

    @Override
    public Long getActiveTokenCount() {
        String val = redisTemplate.opsForValue().get(ACTIVE_TOKEN_COUNT_KEY);
        return val != null ? Long.parseLong(val) : 0L;
    }

    @Override
    public void incrementActiveTokens() {
        redisTemplate.opsForValue().increment(ACTIVE_TOKEN_COUNT_KEY);
    }

    @Override
    public void decrementActiveTokens() {
        redisTemplate.opsForValue().decrement(ACTIVE_TOKEN_COUNT_KEY);
    }
}
