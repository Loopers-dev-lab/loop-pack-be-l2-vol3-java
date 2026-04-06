package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueKeys;
import com.loopers.domain.queue.QueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

@Repository
public class QueueRedisRepository implements QueueRepository {

    /**
     * 크기 상한 ZADD — 기존 멤버는 score 갱신, 신규 멤버는 크기 체크 후 추가.
     * KEYS[1]: waiting-queue key
     * ARGV[1]: maxSize, ARGV[2]: score (timestamp), ARGV[3]: userId
     * @return 1(추가/갱신 성공), 0(만석)
     */
    private static final String ZADD_WITH_LIMIT_LUA =
            "local exists = redis.call('ZSCORE', KEYS[1], ARGV[3]) " +
            "if exists then " +
            "  redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3]) " +
            "  return 1 " +
            "end " +
            "local size = redis.call('ZCARD', KEYS[1]) " +
            "if size >= tonumber(ARGV[1]) then return 0 end " +
            "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3]) " +
            "return 1";

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> defaultRedisTemplate;

    public QueueRedisRepository(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> masterRedisTemplate,
            RedisTemplate<String, String> defaultRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.defaultRedisTemplate = defaultRedisTemplate;
    }

    // Command

    @Override
    public boolean enqueue(Long userId, int maxSize) {
        double score = Instant.now().toEpochMilli() / 1000.0;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ZADD_WITH_LIMIT_LUA, Long.class);
        Long result = masterRedisTemplate.execute(script,
                Collections.singletonList(QueueKeys.WAITING_QUEUE),
                String.valueOf(maxSize),
                String.valueOf(score),
                userId.toString());

        return result != null && result == 1;
    }

    @Override
    public void dequeue(String... userIds) {
        masterRedisTemplate.opsForZSet().remove(QueueKeys.WAITING_QUEUE, (Object[]) userIds);
    }

    // Query

    @Override
    public Set<String> peekTop(int count) {
        return masterRedisTemplate.opsForZSet().range(QueueKeys.WAITING_QUEUE, 0, count - 1);
    }

    @Override
    public Long getRank(Long userId) {
        Long rank = defaultRedisTemplate.opsForZSet().rank(QueueKeys.WAITING_QUEUE, userId.toString());
        return rank != null ? rank + 1 : null;
    }

    @Override
    public long size() {
        Long size = defaultRedisTemplate.opsForZSet().zCard(QueueKeys.WAITING_QUEUE);
        return size != null ? size : 0;
    }
}
