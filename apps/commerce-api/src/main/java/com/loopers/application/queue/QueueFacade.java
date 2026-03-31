package com.loopers.application.queue;

import com.loopers.domain.queue.WaitingQueueService;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class QueueFacade {

    private static final String DEFAULT_EVENT_ID = "default";

    private final WaitingQueueService waitingQueueService;

    public QueueFacade(WaitingQueueService waitingQueueService) {
        this.waitingQueueService = waitingQueueService;
    }

    public QueueInfo joinQueue(Long userId) {
        WaitingQueueService.JoinQueueResult result = waitingQueueService.joinQueue(
            DEFAULT_EVENT_ID,
            userId,
            Instant.now().toEpochMilli()    // score
        );
        return new QueueInfo(result.position(), result.totalWaiting());
    }
}

