package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public class QueueV1Dto {

    public record JoinQueueResponse(
        long position,
        long totalWaiting
    ) {
        public static JoinQueueResponse from(QueueInfo info) {
            return new JoinQueueResponse(
                info.position(),
                info.totalWaiting()
            );
        }
    }
}

