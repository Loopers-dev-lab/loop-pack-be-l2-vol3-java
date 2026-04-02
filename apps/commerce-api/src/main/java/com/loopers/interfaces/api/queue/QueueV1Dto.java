package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueuePollingResult;
import com.loopers.domain.queue.QueuePosition;

public class QueueV1Dto {

    public record EnterQueueResponse(
        long position,
        long estimatedWaitSeconds,
        int retryAfter
    ) {
        public static EnterQueueResponse from(QueuePosition queuePosition) {
            return new EnterQueueResponse(
                queuePosition.position(),
                queuePosition.estimatedWaitSeconds(),
                queuePosition.retryAfter()
            );
        }
    }

    public record PositionResponse(
        String status,
        Long position,
        Long estimatedWaitSeconds,
        Integer retryAfter,
        Long activateAt
    ) {
        public static PositionResponse from(QueuePollingResult result) {
            return switch (result.status()) {
                case WAITING -> new PositionResponse(
                    result.status().name(),
                    result.position().position(),
                    result.position().estimatedWaitSeconds(),
                    result.position().retryAfter(),
                    null
                );
                case TOKEN_ISSUED -> new PositionResponse(
                    result.status().name(),
                    null,
                    null,
                    null,
                    result.token().activateAt()
                );
                case TOKEN_EXPIRED, NOT_IN_QUEUE -> new PositionResponse(
                    result.status().name(),
                    null,
                    null,
                    null,
                    null
                );
            };
        }
    }
}
