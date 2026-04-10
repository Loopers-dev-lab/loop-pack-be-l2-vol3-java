package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.ModeManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisHealthCheckScheduler {

    private static final int RECOVERY_THRESHOLD = 3;

    private final RedisTemplate<String, String> redisTemplate;
    private final ModeManager modeManager;
    private int consecutiveSuccessCount = 0;

    public RedisHealthCheckScheduler(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate,
            ModeManager modeManager
    ) {
        this.redisTemplate = redisTemplate;
        this.modeManager = modeManager;
    }

    @Scheduled(fixedDelay = 10_000)
    public void checkRedisHealth() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            if (pong != null) {
                onSuccess();
            } else {
                onFailure(null);
            }
        } catch (Exception e) {
            onFailure(e);
        }
    }

    private void onSuccess() {
        if (!modeManager.isFallbackMode()) return;

        consecutiveSuccessCount++;
        if (consecutiveSuccessCount >= RECOVERY_THRESHOLD) {
            modeManager.exitFallbackMode();
            consecutiveSuccessCount = 0;
            log.info("Redis 복구 감지 — fallbackMode 해제 (연속 {}회 성공)", RECOVERY_THRESHOLD);
        } else {
            log.info("Redis 복구 진행 중 ({}/{})", consecutiveSuccessCount, RECOVERY_THRESHOLD);
        }
    }

    private void onFailure(Exception e) {
        consecutiveSuccessCount = 0;
        if (!modeManager.isFallbackMode()) {
            modeManager.enterFallbackMode();
            log.error("Redis 장애 감지 — fallbackMode 진입", e);
        }
    }
}
