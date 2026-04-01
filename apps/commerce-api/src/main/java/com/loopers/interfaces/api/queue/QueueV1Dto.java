package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;
import com.loopers.application.queue.QueuePositionInfo;

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

    public record PositionResponse(
            long position,
            long totalWaiting,
            long estimatedWaitSeconds,
            String entryToken,
            long suggestedPollIntervalMs
    ) {
        public static PositionResponse from(QueuePositionInfo info) {
            return new PositionResponse(
                    info.position(),
                    info.totalWaiting(),
                    info.estimatedWaitSeconds(),
                    info.entryToken(),
                    info.suggestedPollIntervalMs()
            );
        }
    }
}

