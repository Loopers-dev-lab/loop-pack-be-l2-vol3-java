package com.loopers.interfaces.api.queue;

public class QueueV1Dto {

    public record EnterRequest(
            Long userId
    ) {
    }

    public record EnterResponse(
            Long rank,
            Long totalWaiting
    ) {
        public static EnterResponse from(com.loopers.application.queue.QueueInfo info) {
            return new EnterResponse(info.rank(), info.totalWaiting());
        }
    }

    public record PositionResponse(
            Long rank,
            Long totalWaiting,
            Long estimatedWaitSeconds,
            String token
    ) {
    }
}
