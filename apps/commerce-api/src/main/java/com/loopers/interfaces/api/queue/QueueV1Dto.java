package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public class QueueV1Dto {

    public record QueuePositionResponse(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        String token,
        Long tokenExpiresIn
    ) {
        public static QueuePositionResponse from(QueueInfo info) {
            return new QueuePositionResponse(
                info.position(), info.totalWaiting(), info.estimatedWaitSeconds(),
                info.token(), info.tokenExpiresIn()
            );
        }
    }
}
