package com.loopers.application.waitingroom;

import com.loopers.domain.waitingroom.WaitingEntry;
import com.loopers.domain.waitingroom.WaitingRoomService;
import com.loopers.infrastructure.waitingroom.metrics.WaitingRoomProcessQueueMetrics;
import com.loopers.infrastructure.waitingroom.metrics.WaitingRoomSchedulerTimer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기열 스케줄러.
 * 100ms마다 2명씩 꺼내서 1초에 최대 20명 입장 (Jitter 효과).
 * 1초에 20명을 한 번에 꺼내면 20명이 동시에 주문 API로 몰리는 Thundering Herd 발생.
 * 100ms 간격으로 분산하면 부하가 10배 평탄화된다.
 */
@Component
@RequiredArgsConstructor
public class WaitingRoomScheduler {

    private final WaitingRoomService waitingRoomService;
    private final WaitingRoomSchedulerTimer schedulerTimer;
    private final WaitingRoomProcessQueueMetrics processQueueMetrics;

    @Scheduled(fixedDelay = 100)
    public void run() {
        schedulerTimer.record(() -> {
            List<WaitingEntry> admitted = waitingRoomService.processQueue();
            processQueueMetrics.recordBatch(admitted);
        });
    }
}
