package com.loopers.infrastructure.queue;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;

@Repository
public class QueueStatusLuaRepository {

    private static final String LUA_SCRIPT = """
            local tokenKey = KEYS[1]
            local waitingKey = KEYS[2]
            local heartbeatKey = KEYS[3]
            local memberId = ARGV[1]

            local healthy = redis.call('EXISTS', heartbeatKey)
            local token = redis.call('GET', tokenKey)
            if token then
                local total = redis.call('ZCARD', waitingKey)
                return healthy .. '|TOKEN|' .. token .. '|' .. total
            end
            local rank = redis.call('ZRANK', waitingKey, memberId)
            if not rank then
                return healthy .. '|NOT_FOUND'
            end
            local total = redis.call('ZCARD', waitingKey)
            return healthy .. '|WAITING|' .. (rank + 1) .. '|' .. total
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<String> script;

    public QueueStatusLuaRepository(
            @Qualifier(REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, String.class);
    }

    public QueueStatusResult getStatus(Long memberId, String heartbeatSchedulerName) {
        String tokenKey = "queue:token:" + memberId;
        String waitingKey = "queue:waiting";
        String heartbeatKey = "queue:heartbeat:" + heartbeatSchedulerName;

        String result = redisTemplate.execute(
                script,
                List.of(tokenKey, waitingKey, heartbeatKey),
                String.valueOf(memberId)
        );

        return QueueStatusResult.parse(result);
    }

    public record QueueStatusResult(
            boolean healthy,
            String status,
            String token,
            long position,
            long totalInQueue
    ) {
        static QueueStatusResult parse(String raw) {
            String[] parts = raw.split("\\|", -1);
            boolean healthy = "1".equals(parts[0]);
            String status = parts[1];

            if ("TOKEN".equals(status)) {
                return new QueueStatusResult(healthy, status, parts[2], 0, Long.parseLong(parts[3]));
            }
            if ("NOT_FOUND".equals(status)) {
                return new QueueStatusResult(healthy, status, null, 0, 0);
            }
            return new QueueStatusResult(healthy, status, null, Long.parseLong(parts[2]), Long.parseLong(parts[3]));
        }
    }
}
