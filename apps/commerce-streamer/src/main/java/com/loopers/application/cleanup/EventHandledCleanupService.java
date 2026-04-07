package com.loopers.application.cleanup;

import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * event_handled 멱등 테이블 보관 주기: 오래된 행을 삭제해 쓰기 부하·테이블 크기를 완화한다.
 */
@Service
public class EventHandledCleanupService {

    private final EventHandledJpaRepository eventHandledJpaRepository;

    public EventHandledCleanupService(EventHandledJpaRepository eventHandledJpaRepository) {
        this.eventHandledJpaRepository = eventHandledJpaRepository;
    }

    /**
     * {@code handled_at}이 cutoff 이전인 행을 최대 {@code limit}건 삭제한다.
     */
    @Transactional
    public int deleteOlderThan(Instant cutoff, int limit) {
        return eventHandledJpaRepository.deleteHandledBefore(cutoff, limit);
    }

    /**
     * 보관 기한이 지난 행을 batchSize 단위로 삭제하되, 스케줄 한 번당 루프 횟수·총 삭제 행 수 상한을 둔다.
     *
     * @return 실제 삭제된 행 수 합계
     */
    @Transactional
    public int deleteOlderThanWithinSchedule(
            Instant cutoff, int batchSize, int maxLoopsPerRun, long maxRowsPerRun) {
        int totalDeleted = 0;
        int loops = 0;
        while (loops < maxLoopsPerRun && totalDeleted < maxRowsPerRun) {
            long remainingBudget = maxRowsPerRun - totalDeleted;
            int limit = (int) Math.min(batchSize, remainingBudget);
            if (limit <= 0) {
                break;
            }
            int deleted = deleteOlderThan(cutoff, limit);
            totalDeleted += deleted;
            loops++;
            if (deleted == 0) {
                break;
            }
        }
        return totalDeleted;
    }

    public Instant retentionCutoff(int retentionDays) {
        return Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    }
}
