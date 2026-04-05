package com.loopers.application.queue;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class QueueAdmissionScheduler {

    private final QueueService queueService;
    private final QueueThroughputPolicy throughputPolicy;

    public QueueAdmissionScheduler(QueueService queueService, QueueThroughputPolicy throughputPolicy) {
        this.queueService = queueService;
        this.throughputPolicy = throughputPolicy;
    }

    @Scheduled(fixedDelayString = "${queue.scheduler.fixed-delay-ms:1000}")
    @SchedulerLock(name = "admitWaitingUsers", lockAtLeastFor = "PT1S")
    public void admitWaitingUsers() {
        int batchSize = throughputPolicy.calculateBatchSize();
        Map<Long, String> issuedTokens = queueService.admitNextBatch(batchSize);
        if (!issuedTokens.isEmpty()) {
            log.info("[queue-admission] issued={} batchSize={} waiting={}", issuedTokens.size(), batchSize, queueService.getTotalWaitingCount());
        }
    }
}
