package com.loopers.batch;

import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.idempotency.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 이벤트 테이블 정리 배치.
 *
 * ★ TX 범위 설계:
 *   이 스케줄러 메서드에는 @Transactional을 붙이지 않는다.
 *   Repository의 deleteOlderThan()에 @Transactional이 붙어 있어
 *   각 LIMIT DELETE가 독립 TX로 실행된다.
 *   → 수백만 건 삭제가 1 TX로 묶이는 것을 방지 (undo log 폭증, 행 락 장기 유지 방지).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventCleanupScheduler {

    private final EventHandledRepository eventHandledRepository;
    private final EventLogRepository eventLogRepository;

    private static final int BATCH_SIZE = 10_000;

    /** event_handled 정리 — 매일 04:00. 30일 이전 레코드 삭제. */
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupEventHandled() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int totalDeleted = 0;
        int deleted;

        do {
            deleted = eventHandledRepository.deleteOlderThan(cutoff, BATCH_SIZE);
            totalDeleted += deleted;
        } while (deleted == BATCH_SIZE);

        log.info("[event_handled정리] 완료: {} 건 삭제 (cutoff={})", totalDeleted, cutoff);
    }

    /** event_log 정리 — 매주 일요일 02:00. 90일 이전 레코드 삭제. */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void cleanupEventLog() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int totalDeleted = 0;
        int deleted;

        do {
            deleted = eventLogRepository.deleteOlderThan(cutoff, BATCH_SIZE);
            totalDeleted += deleted;
        } while (deleted == BATCH_SIZE);

        log.info("[event_log정리] 완료: {} 건 삭제 (cutoff={})", totalDeleted, cutoff);
    }
}
