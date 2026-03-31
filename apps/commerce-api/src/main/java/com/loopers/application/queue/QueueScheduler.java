package com.loopers.application.queue;

import com.loopers.config.QueueProperties;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private static final int HEARTBEAT_TTL_SECONDS = 5;

    private final WaitingQueueService waitingQueueService;
    private final EntryTokenService entryTokenService;
    private final QueueProperties queueProperties;
    private final ThroughputTracker throughputTracker;
    private final SchedulerHealthChecker schedulerHealthChecker;

    @Scheduled(fixedDelayString = "${queue.interval-ms}")
    @SchedulerLock(name = "queue_token_issuer", lockAtMostFor = "PT1S", lockAtLeastFor = "PT0S")
    public void issueTokens() {
        if (!queueProperties.enabled()) {
            return;
        }

        schedulerHealthChecker.recordTick(HEARTBEAT_TTL_SECONDS);

        List<Map.Entry<Long, Double>> entries = waitingQueueService.popNWithScore(queueProperties.batchSize());
        if (entries.isEmpty()) {
            return;
        }

        for (Map.Entry<Long, Double> entry : entries) {
            Long memberId = entry.getKey();
            entryTokenService.issue(memberId);
        }

        throughputTracker.recordIssued(entries.size());
        log.info("[QUEUE_SCHEDULER] issued={}", entries.size());
    }
}
