package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueueEntryResult;
import com.loopers.domain.queue.QueuePositionResult;

public class QueueV1Dto {

    public record EnterResponse(
            long position,
            long totalWaiting,
            long estimatedWaitSeconds,
            boolean isNew
    ) {
        public static EnterResponse from(QueueEntryResult result) {
            return new EnterResponse(
                    result.position(),
                    result.totalWaiting(),
                    result.estimatedWaitSeconds(),
                    result.isNew()
            );
        }
    }

    public record PositionResponse(
            String status,
            Long position,
            Long totalWaiting,
            Long estimatedWaitSeconds,
            String token
    ) {
        public static PositionResponse from(QueuePositionResult result) {
            return new PositionResponse(
                    result.status().name(),
                    result.position(),
                    result.totalWaiting(),
                    result.estimatedWaitSeconds(),
                    result.token()
            );
        }
    }
}
