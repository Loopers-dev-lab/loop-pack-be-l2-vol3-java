package com.loopers.application.queue;

import com.loopers.domain.queue.WaitingQueue;

public record QueueInfo(long position, long totalWaiting, long estimatedWaitSeconds, String token) {

    public static QueueInfo from(WaitingQueue queue, int tps) {
        return new QueueInfo(
            queue.getPosition(),
            queue.getTotalWaiting(),
            queue.calculateEstimatedWaitSeconds(tps),
            null
        );
    }

    public static QueueInfo admitted(String token) {
        return new QueueInfo(0, 0, 0, token);
    }
}
