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

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final WaitingQueueService waitingQueueService;
    private final EntryTokenService entryTokenService;
    private final QueueProperties queueProperties;

    @Scheduled(fixedDelayString = "${queue.interval-ms}")
    @SchedulerLock(name = "queue_token_issuer", lockAtMostFor = "PT1S", lockAtLeastFor = "PT100MS")
    public void issueTokens() {
        if (!queueProperties.enabled()) {
            return;
        }

        List<Long> memberIds = waitingQueueService.popN(queueProperties.batchSize());
        if (memberIds.isEmpty()) {
            return;
        }

        for (Long memberId : memberIds) {
            entryTokenService.issue(memberId);
        }

        log.info("[QUEUE_SCHEDULER] issued={}", memberIds.size());
    }
}
