package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueDto;

public class QueueV1Dto {

    public record QueueEntryResponse(
            Long position,
            Long totalWaiting,
            Long estimatedWaitSeconds,
            Long nextPollAfterSeconds
    ) {
        public static QueueEntryResponse from(QueueDto.QueueEntryResult result) {
            return new QueueEntryResponse(
                    result.position(),
                    result.totalWaiting(),
                    result.estimatedWaitSeconds(),
                    result.nextPollAfterSeconds()
            );
        }
    }

    public record QueuePositionResponse(
            Long position,
            Long totalWaiting,
            Long estimatedWaitSeconds,
            String token,
            Long nextPollAfterSeconds
    ) {
        public static QueuePositionResponse from(QueueDto.QueuePositionResult result) {
            return new QueuePositionResponse(
                    result.position(),
                    result.totalWaiting(),
                    result.estimatedWaitSeconds(),
                    result.token(),
                    result.nextPollAfterSeconds()
            );
        }
    }
}
