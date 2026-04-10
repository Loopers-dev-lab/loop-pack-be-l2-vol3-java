package com.loopers.application.queue;

import com.loopers.infrastructure.queue.QueueCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Component
public class QueueScheduler {

    /**
     * 처리량 설계 기준:
     * - DB 커넥션 풀: 50, 주문 평균 처리 시간: 200ms
     * - 최대 TPS = 50 / 0.2 = 250, 안전 마진 70% = 175 TPS
     * - Thundering Herd 완화: 100ms마다 ~18명씩 발급 (175 / 10 ≈ 18)
     */
    static final int BATCH_SIZE = 18;

    private final QueueCacheService queueCacheService;

    @Scheduled(fixedDelay = 100)
    public void processQueue() {
        Set<ZSetOperations.TypedTuple<String>> entries = queueCacheService.popN(BATCH_SIZE);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        for (ZSetOperations.TypedTuple<String> entry : entries) {
            String userIdStr = entry.getValue();
            if (userIdStr == null) {
                continue;
            }
            try {
                Long userId = Long.parseLong(userIdStr);
                queueCacheService.issueToken(userId);
                log.debug("[Queue] 입장 토큰 발급: userId={}", userId);
            } catch (Exception e) {
                log.error("[Queue] 토큰 발급 실패: userId={}", userIdStr, e);
            }
        }
    }
}
