package com.loopers.domain.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 대기열 → 입장 토큰 발급 스케줄러.
 *
 * <p>100ms마다 ZPOPMIN으로 대기열 앞 N명을 꺼내고 각각에게 토큰을 발급한다.
 * 다중 인스턴스 환경에서 이중 실행을 방지하기 위해 Redis SETNX 리더 선출을 사용한다.</p>
 *
 * <p>처리량: batchSize(14) × 10회/초 = 140 TPS</p>
 *
 * <p>안전장치:
 * <ul>
 *   <li>owner 검증 Lua unlock — 다른 인스턴스의 락을 삭제하지 않음</li>
 *   <li>failedEntries 버퍼 — 보상 실패 시 다음 주기에 재시도</li>
 * </ul></p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "queue.scheduler.enabled", havingValue = "true")
public class OrderQueueScheduler {

    private final QueueService queueService;
    private final QueueRepository queueRepository;
    private final EntryTokenService entryTokenService;
    private final QueueProperties queueProperties;
    private final SchedulerLock schedulerLock;

    /** 보상 실패한 유저를 다음 주기에 재시도하기 위한 버퍼. */
    private final List<QueueEntry> failedEntries =
            Collections.synchronizedList(new ArrayList<>());

    public OrderQueueScheduler(
            QueueService queueService,
            QueueRepository queueRepository,
            EntryTokenService entryTokenService,
            QueueProperties queueProperties,
            SchedulerLock schedulerLock) {
        this.queueService = queueService;
        this.queueRepository = queueRepository;
        this.entryTokenService = entryTokenService;
        this.queueProperties = queueProperties;
        this.schedulerLock = schedulerLock;
    }

    /**
     * 100ms 주기로 대기열을 처리한다.
     */
    @Scheduled(fixedRateString = "${queue.scheduler.interval-ms:100}",
               scheduler = "queueTaskScheduler")
    public void processQueue() {
        // 1. 리더 선출
        if (!schedulerLock.tryAcquire()) {
            return;
        }

        try {
            // 이전 주기 보상 실패분 재시도
            retryFailedEntries();

            // 2. ZPOPMIN으로 배치 추출
            int batchSize = queueProperties.getSchedulerBatchSize();
            List<QueueEntry> entries = queueService.popBatch(batchSize);

            if (entries.isEmpty()) {
                return;
            }

            // 3. 각각 토큰 발급 + 실패 보상
            int successCount = 0;
            for (QueueEntry entry : entries) {
                try {
                    entryTokenService.issueToken(entry.userId());
                    successCount++;
                } catch (Exception e) {
                    log.warn("토큰 발급 실패, 대기열 복귀: userId={}", entry.userId(), e);
                    compensateFailure(entry);
                }
            }

            if (successCount > 0) {
                log.debug("토큰 발급 완료: {}명", successCount);
            }
        } finally {
            schedulerLock.release();
        }
    }

    private void compensateFailure(QueueEntry entry) {
        try {
            queueRepository.addIfAbsent(entry.userId(), entry.score());
            log.info("대기열 복귀 완료: userId={}, score={}", entry.userId(), entry.score());
        } catch (Exception e) {
            log.error("대기열 복귀 실패, 재시도 버퍼에 저장: userId={}", entry.userId(), e);
            failedEntries.add(entry);
        }
    }

    private void retryFailedEntries() {
        if (failedEntries.isEmpty()) {
            return;
        }

        List<QueueEntry> snapshot = new ArrayList<>(failedEntries);
        failedEntries.clear();

        for (QueueEntry entry : snapshot) {
            try {
                queueRepository.addIfAbsent(entry.userId(), entry.score());
                log.info("재시도 복귀 성공: userId={}", entry.userId());
            } catch (Exception e) {
                log.error("재시도 복귀 실패: userId={}", entry.userId(), e);
                failedEntries.add(entry);
            }
        }
    }

}
