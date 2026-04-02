package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis 기반 대기열 Repository 구현
 *
 * Redis 자료구조:
 * - Waiting Queue: Sorted Set (key=queue:waiting, score=timestamp, member=userId)
 * - Entry Token: String (key=queue:token:{userId}, value="active", TTL)
 *
 * 원자성 보장:
 * - ZPOPMIN → SET 전환은 Lua Script로 원자적으로 처리
 * - ZADD NX로 중복 진입 방지
 */
@Repository
public class RedisQueueRepository implements QueueRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisQueueRepository.class);

    private static final String WAITING_QUEUE_KEY = "queue:waiting";
    private static final String TOKEN_KEY_PREFIX = "queue:token:";
    private static final String ACTIVE_COUNT_KEY = "queue:active:count";

    /**
     * Lua Script: 대기열 앞쪽에서 N명을 꺼내고 토큰을 발급한다 (원자적)
     *
     * KEYS[1] = queue:waiting (Sorted Set)
     * ARGV[1] = count (꺼낼 수)
     * ARGV[2] = TTL (토큰 유효시간, 초)
     * ARGV[3] = token key prefix ("queue:token:")
     *
     * 반환: 활성화된 userId 목록
     */
    private static final String ACTIVATE_LUA_SCRIPT = """
            local queue_key = KEYS[1]
            local count = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local prefix = ARGV[3]

            local members = redis.call('ZPOPMIN', queue_key, count)
            local activated = {}

            for i = 1, #members, 2 do
                local userId = members[i]
                local tokenKey = prefix .. userId
                redis.call('SET', tokenKey, 'active', 'EX', ttl)
                table.insert(activated, userId)
            end

            return activated
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> activateScript;

    public RedisQueueRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.activateScript = new DefaultRedisScript<>();
        this.activateScript.setScriptText(ACTIVATE_LUA_SCRIPT);
        this.activateScript.setResultType(List.class);
    }

    @Override
    public boolean addToWaitingQueue(Long userId, double score) {
        Boolean added = redisTemplate.opsForZSet()
                .addIfAbsent(WAITING_QUEUE_KEY, userId.toString(), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> getPosition(Long userId) {
        Long rank = redisTemplate.opsForZSet()
                .rank(WAITING_QUEUE_KEY, userId.toString());
        return Optional.ofNullable(rank);
    }

    @Override
    public long getTotalWaiting() {
        Long size = redisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY);
        return size != null ? size : 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> activateFromQueue(int count, int tokenTtlSeconds) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        List<String> result = redisTemplate.execute(
                activateScript,
                List.of(WAITING_QUEUE_KEY),
                String.valueOf(count),
                String.valueOf(tokenTtlSeconds),
                TOKEN_KEY_PREFIX
        );

        if (result == null || result.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> activated = new ArrayList<>();
        for (Object item : result) {
            try {
                activated.add(Long.parseLong(item.toString()));
            } catch (NumberFormatException e) {
                log.warn("토큰 활성화 파싱 실패: {}", item);
            }
        }
        return activated;
    }

    @Override
    public long getTokenTtl(Long userId) {
        String tokenKey = TOKEN_KEY_PREFIX + userId;
        Long ttl = redisTemplate.getExpire(tokenKey);
        return ttl != null ? ttl : -2;
    }

    @Override
    public boolean hasValidToken(Long userId) {
        String tokenKey = TOKEN_KEY_PREFIX + userId;
        Boolean exists = redisTemplate.hasKey(tokenKey);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void deleteToken(Long userId) {
        String tokenKey = TOKEN_KEY_PREFIX + userId;
        redisTemplate.delete(tokenKey);
    }

    @Override
    public void removeFromWaitingQueue(Long userId) {
        redisTemplate.opsForZSet().remove(WAITING_QUEUE_KEY, userId.toString());
    }

    @Override
    public long getActiveTokenCount() {
        Set<String> keys = redisTemplate.keys(TOKEN_KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }
}
