package com.loopers.domain.queue;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Component
public class TokenScheduler {

    private static final String LOCK_KEY = "lock:token-scheduler";

    // Lua 스크립트에서 접두사는 QueueConstants 상수와 동일
    // 'token:'    → QueueConstants.TOKEN_KEY_PREFIX
    // 'presence:' → QueueConstants.PRESENCE_KEY_PREFIX
    private static final String POP_ELIGIBLE_USERS_SCRIPT = """
            local members = redis.call('ZRANGE', KEYS[1], 0, ARGV[1])
            local eligible = {}
            for i, member in ipairs(members) do
                if redis.call('EXISTS', 'token:' .. member) == 0 then
                    redis.call('ZREM', KEYS[1], member)
                    if redis.call('EXISTS', 'presence:' .. member) == 1 then
                        table.insert(eligible, member)
                    end
                end
            end
            return eligible
            """;

    private static final DefaultRedisScript<List<String>> POP_SCRIPT =
            new DefaultRedisScript<>(POP_ELIGIBLE_USERS_SCRIPT, (Class<List<String>>) (Class<?>) List.class);

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final TokenService tokenService;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 5000)
    public void issueTokens() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 4, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("토큰 스케줄러 락 획득 실패 - 다른 인스턴스 실행 중");
                return;
            }
            executeIssue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void executeIssue() {
        List<String> eligible = redisTemplate.execute(
                POP_SCRIPT,
                List.of(QueueConstants.QUEUE_KEY),
                String.valueOf(QueueConstants.BATCH_SIZE - 1) // ZRANGE end index (0-indexed)
        );

        if (eligible == null || eligible.isEmpty()) {
            return;
        }

        for (String userId : eligible) {
            tokenService.issue(userId);
        }
        meterRegistry.counter("queue.tokens.issued.total").increment(eligible.size());
        log.info("토큰 발급 완료: {}명", eligible.size());
    }
}