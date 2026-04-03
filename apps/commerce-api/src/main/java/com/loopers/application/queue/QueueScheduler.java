package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기열 배치 입장 스케줄러.
 *
 * 설계 결정: 스케줄러가 유일한 admitter.
 * pull(폴링 감지) 방식에서 push(스케줄러) 방식으로 전환한 이유:
 * - pull + push 동시 존재 시 이중 입장 가능성 → 스케줄러로 단일 진입점 확보
 * - 초당 정확히 N명 제어 용이
 *
 * fixedDelay vs fixedRate:
 * - fixedRate: 이전 실행 완료 여부와 무관하게 N ms마다 실행 → 중첩 실행 가능
 * - fixedDelay: 이전 실행 완료 후 N ms 대기 → 중첩 없음 (선택)
 * Redis 응답 지연 시 중첩 실행이 발생하면 batchSize 초과 위험 → fixedDelay 선택.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class QueueScheduler {

    private static final String QUEUE_ID = "bf-2024";

    private final QueueService queueService;

    /**
     * 1초마다 THROUGHPUT_PER_SECOND명을 대기열에서 꺼내 입장 허가.
     * 실패해도 다음 주기에 재시도 (예외 삼킴 → 스케줄러 종료 방지).
     */
    @Scheduled(fixedDelay = 1000)
    public void admitBatch() {
        try {
            queueService.processBatch(QUEUE_ID);
        } catch (Exception e) {
            log.warn("[Queue] Scheduler failed. Will retry next cycle.", e);
        }
    }
}
