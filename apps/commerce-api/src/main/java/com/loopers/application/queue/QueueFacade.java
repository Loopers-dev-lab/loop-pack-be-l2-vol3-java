package com.loopers.application.queue;

import com.loopers.domain.queue.QueueConstants;
import com.loopers.domain.queue.QueueService;
import com.loopers.domain.queue.TokenService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    // Adaptive Polling: 순번 구간별 nextPollAfter
    private static final long NEAR_THRESHOLD = 10;
    private static final long MID_THRESHOLD = 50;
    private static final long NEAR_POLL_SECONDS = 5;
    private static final long MID_POLL_SECONDS = 15;
    private static final long FAR_POLL_SECONDS = 30;

    private final QueueService queueService;
    private final TokenService tokenService;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void initMetrics() {
        Gauge.builder("queue.size", queueService, QueueService::getTotalCount)
                .description("현재 대기열 크기")
                .register(meterRegistry);
    }

    public QueueInfo enter(String userId) {
        long position = queueService.enter(userId);
        long totalCount = queueService.getTotalCount();
        meterRegistry.counter("queue.enter.total").increment();
        return new QueueInfo(position, totalCount);
    }

    public QueuePositionInfo getPosition(String userId) {
        queueService.refreshPresence(userId);
        long position = queueService.getPosition(userId);
        long totalCount = queueService.getTotalCount();
        long estimatedWaitSeconds = calculateEstimatedWait(position);
        long nextPollAfterSeconds = calculateNextPollAfter(position);
        String token = tokenService.findToken(userId).orElse(null);
        return new QueuePositionInfo(position, totalCount, estimatedWaitSeconds, nextPollAfterSeconds, token);
    }

    private long calculateEstimatedWait(long position) {
        return (long) Math.ceil((double) position / QueueConstants.BATCH_SIZE * QueueConstants.SCHEDULER_INTERVAL_SECONDS);
    }

    private long calculateNextPollAfter(long position) {
        if (position <= NEAR_THRESHOLD) {
            return NEAR_POLL_SECONDS;
        } else if (position <= MID_THRESHOLD) {
            return MID_POLL_SECONDS;
        }
        return FAR_POLL_SECONDS;
    }
}