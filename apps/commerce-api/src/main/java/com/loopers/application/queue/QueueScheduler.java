package com.loopers.application.queue;

import com.loopers.infrastructure.queue.QueueCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

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

    /**
     * 대기열 GC 기준: 10분 이상 경과한 항목을 만료로 간주.
     * 서버 장애 등으로 처리되지 못한 ZADD 항목이 무한 잔류하는 것을 방지.
     */
    static final long STALE_THRESHOLD_MS = 10 * 60 * 1000L;

    private final QueueCacheService queueCacheService;
    private final ReentrantLock processLock = new ReentrantLock();

    /**
     * fixedRate: 이전 배치 완료 여부와 무관하게 100ms 간격으로 실행.
     * ReentrantLock으로 이전 배치가 아직 처리 중이면 스킵하여 중복 실행 방지.
     */
    @Scheduled(fixedRate = 100)
    public void processQueue() {
        if (!processLock.tryLock()) {
            return;
        }
        try {
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
        } finally {
            processLock.unlock();
        }
    }

    /**
     * 대기열 GC: 1분마다 10분 이상 경과한 항목 제거.
     * ZADD 항목은 TTL이 없으므로 장애 시 잔류 항목이 누적될 수 있음.
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanStaleQueueEntries() {
        long removed = queueCacheService.cleanStaleEntries(STALE_THRESHOLD_MS);
        if (removed > 0) {
            log.info("[Queue] 만료 대기열 항목 정리: {}건", removed);
        }
    }
}
