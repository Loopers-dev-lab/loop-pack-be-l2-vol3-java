package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueuePositionProperties;
import com.loopers.config.redis.RedisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code X-Loopers-LoginId} 기준으로 순번 API·SSE 경로의 초당 요청 수를 제한한다.
 * <p>
 * Redis INCR + EXPIRE를 Lua로 원자화한다. Redis 장애 시에는 요청을 통과시킨다(fail-open).
 */
@Component
public class RedisQueuePositionRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisQueuePositionRateLimiter.class);

    private static final String KEY_PREFIX = "queue:ratelimit:position:";

    private static final String LUA = """
        local c = redis.call('INCR', KEYS[1])
        if c == 1 then
          redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
        end
        if c > tonumber(ARGV[1]) then
          return 0
        end
        return 1
        """;

    private final RedisTemplate<String, String> redisTemplate;
    private final QueuePositionProperties positionProperties;
    private final DefaultRedisScript<Long> script;

    public RedisQueuePositionRateLimiter(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
            QueuePositionProperties positionProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.positionProperties = positionProperties;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(LUA);
        this.script.setResultType(Long.class);
    }

    /**
     * 허용되면 {@code true}, 상한 초과 시 {@code false}. 비활성화·loginId 공백이면 {@code true}.
     */
    public boolean tryAcquire(String loginId) {
        var rl = positionProperties.rateLimit();
        if (!rl.enabled() || loginId == null || loginId.isBlank()) {
            return true;
        }
        String key = KEY_PREFIX + loginId.trim();
        try {
            Long ok = redisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(rl.maxRequestsPerSecond()),
                    String.valueOf(rl.windowSeconds())
            );
            return ok != null && ok == 1L;
        } catch (RuntimeException e) {
            log.warn("queue position rate limit skipped (Redis error): {}", e.toString());
            return true;
        }
    }
}
