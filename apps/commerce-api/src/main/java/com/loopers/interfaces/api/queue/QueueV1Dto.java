package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public class QueueV1Dto {

    public record EnterResponse(long position, long totalWaiting, long estimatedWaitSeconds) {
        public static EnterResponse from(QueueInfo info) {
            return new EnterResponse(
                info.position(),
                info.totalWaiting(),
                info.estimatedWaitSeconds()
            );
        }
    }

    public record PositionResponse(long position, long totalWaiting, long estimatedWaitSeconds, String token) {
        public static PositionResponse from(QueueInfo info) {
            return new PositionResponse(
                info.position(),
                info.totalWaiting(),
                info.estimatedWaitSeconds(),
                info.token()
            );
        }
    }
}
