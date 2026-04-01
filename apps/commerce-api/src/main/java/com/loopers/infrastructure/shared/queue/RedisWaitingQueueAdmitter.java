package com.loopers.infrastructure.shared.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.queue.WaitingQueueAdmitter;

/**
 * Lua 스크립트 기반 대기열 → 토큰 발급 원자적 전환 구현체.
 *
 * <p>ZRANGE(조회) → SET NX EX(토큰 저장) → ZREM(대기열 제거)를
 * 단일 Lua 스크립트로 실행하여 원자성을 보장한다.</p>
 */
@Component
public class RedisWaitingQueueAdmitter implements WaitingQueueAdmitter {

    private static final String WAITING_KEY = "queue:waiting";
    private static final int TOKEN_TTL_SECONDS = 120;

    private static final String TRANSFER_SCRIPT = """
            local waiting_key = KEYS[1]
            local count = tonumber(ARGV[1])
            local ttl_seconds = tonumber(ARGV[2])

            local members = redis.call('ZRANGE', waiting_key, 0, count - 1)
            if #members == 0 then
                return {}
            end

            for i, member in ipairs(members) do
                local token = ARGV[i + 2]
                redis.call('SET', 'entry-token:' .. member, token, 'NX', 'EX', ttl_seconds)
            end

            redis.call('ZREM', waiting_key, unpack(members))

            return members
            """;

    private final RedisTemplate<String, String> redisTemplate;

    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> script;

    public RedisWaitingQueueAdmitter(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(TRANSFER_SCRIPT, List.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Long> admit(int count) {
        List<String> keys = List.of(WAITING_KEY);

        List<String> args = new ArrayList<>(count + 2);
        args.add(String.valueOf(count));
        args.add(String.valueOf(TOKEN_TTL_SECONDS));
        for (int i = 0; i < count; i++) {
            args.add(UUID.randomUUID().toString());
        }

        List<String> result = redisTemplate.execute(
                script, keys, (Object[]) args.toArray(String[]::new)
        );

        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        return result.stream()
                .map(Long::valueOf)
                .toList();
    }
}
