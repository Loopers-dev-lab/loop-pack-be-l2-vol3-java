package com.loopers.application.queue;

import com.loopers.config.QueueProperties;
import com.loopers.domain.queue.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class QueueApp {

    private final WaitingQueueService waitingQueueService;
    private final QueueProperties queueProperties;

    public QueueInfo enterQueue(Long memberId) {
        waitingQueueService.enter(memberId);

        Optional<Long> position = waitingQueueService.getPosition(memberId);
        long pos = position.orElse(0L);
        long estimatedWaitSeconds = calculateEstimatedWaitSeconds(pos);

        return new QueueInfo(pos, estimatedWaitSeconds, null);
    }

    private long calculateEstimatedWaitSeconds(long position) {
        if (position <= 0) {
            return 0;
        }
        return (long) Math.ceil((double) position / queueProperties.throughputPerSecond());
    }
}
