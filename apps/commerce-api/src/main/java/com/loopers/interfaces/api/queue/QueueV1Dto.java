package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;
import com.loopers.application.queue.QueuePositionInfo;

public class QueueV1Dto {

    public record EnterResponse(
            Long userId,
            long position,
            long totalWaiting,
            boolean newEntry
    ) {
        public static EnterResponse from(QueueInfo info) {
            return new EnterResponse(info.userId(), info.position(), info.totalWaiting(), info.newEntry());
        }
    }

    public record PositionResponse(
            long position,
            long totalWaiting,
            long estimatedWaitSeconds,
            int pollingIntervalSeconds,
            String token
    ) {
        public static PositionResponse from(QueuePositionInfo info) {
            return new PositionResponse(
                    info.position(), info.totalWaiting(),
                    info.estimatedWaitSeconds(), info.pollingIntervalSeconds(),
                    info.token()
            );
        }
    }
}
