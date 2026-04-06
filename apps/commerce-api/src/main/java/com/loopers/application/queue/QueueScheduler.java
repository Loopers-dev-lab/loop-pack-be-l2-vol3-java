package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(QueueProperties.class)
public class QueueScheduler {

    private final RedisTemplate<String, String> redisTemplate;
    private final TokenService tokenService;
    private final QueueProperties queueProperties;

    @Scheduled(fixedDelay = 100)
    public void scheduledProcessQueue() {
        if (!queueProperties.schedulerEnabled()) {
            return;
        }
        processQueue();
    }

    public void processQueue() {
        int batchSize = Math.max(1, queueProperties.throughputPerSecond() / 10);

        Set<ZSetOperations.TypedTuple<String>> popped =
            redisTemplate.opsForZSet().popMin(queueProperties.redisKey(), batchSize);

        if (popped == null || popped.isEmpty()) {
            return;
        }

        List<String> failedUserIds = new ArrayList<>();

        for (ZSetOperations.TypedTuple<String> tuple : popped) {
            String userIdStr = tuple.getValue();
            if (userIdStr == null) {
                continue;
            }

            try {
                Long userId = Long.parseLong(userIdStr);
                String token = tokenService.issueToken(userId);
                log.info("Entry token issued: userId={}, token={}", userId, token);
            } catch (Exception e) {
                log.error("Failed to issue token for userId={}, will re-enqueue", userIdStr, e);
                failedUserIds.add(userIdStr);
            }
        }

        // 실패한 유저들을 대기열에 재진입
        if (!failedUserIds.isEmpty()) {
            reEnqueueFailedUsers(failedUserIds);
        }

        log.info("Processed {} users from queue, {} failed", popped.size(), failedUserIds.size());
    }

    private void reEnqueueFailedUsers(List<String> userIds) {
        double score = System.currentTimeMillis();
        for (String userId : userIds) {
            try {
                // 실패한 유저는 현재 시간으로 재진입 (맨 뒤로)
                redisTemplate.opsForZSet().add(queueProperties.redisKey(), userId, score);
                log.info("Re-enqueued failed userId={}", userId);
            } catch (Exception e) {
                log.error("Failed to re-enqueue userId={}", userId, e);
            }
        }
    }
}