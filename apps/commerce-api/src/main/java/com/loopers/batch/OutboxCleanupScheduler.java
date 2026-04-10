package com.loopers.batch;

import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Outbox 테이블 정리 배치 — 매일 03:00.
 * - PUBLISHED: 7일 이전 삭제
 * - DEAD: 30일 이전 삭제
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupScheduler {

    private final OutboxEventRepository outboxEventRepository;

    private static final int BATCH_SIZE = 10_000;

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOutbox() {
        int publishedDeleted = deleteInBatches(
                () -> outboxEventRepository.deletePublishedOlderThan(
                        LocalDateTime.now().minusDays(7), BATCH_SIZE));
        int deadDeleted = deleteInBatches(
                () -> outboxEventRepository.deleteDeadOlderThan(
                        LocalDateTime.now().minusDays(30), BATCH_SIZE));

        log.info("[outbox정리] 완료: PUBLISHED {} 건, DEAD {} 건 삭제", publishedDeleted, deadDeleted);
    }

    private int deleteInBatches(java.util.function.IntSupplier deleteFunction) {
        int totalDeleted = 0;
        int deleted;
        do {
            deleted = deleteFunction.getAsInt();
            totalDeleted += deleted;
        } while (deleted == BATCH_SIZE);
        return totalDeleted;
    }
}
