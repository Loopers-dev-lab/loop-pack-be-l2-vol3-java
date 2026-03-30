package com.loopers.interfaces.api.queue.v1;

import com.loopers.application.queue.QueuePositionResult;

public class QueueDto {

    public record PositionResponse(
            long position,
            long totalWaiting,
            long estimatedWaitSeconds
    ) {

        public static PositionResponse from(QueuePositionResult result) {
            return new PositionResponse(
                    result.position(),
                    result.totalWaiting(),
                    result.estimatedWaitSeconds()
            );
        }
    }
}
