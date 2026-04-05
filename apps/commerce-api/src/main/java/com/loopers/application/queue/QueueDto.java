package com.loopers.application.queue;

public class QueueDto {

    public record QueueEntryResult(
            Long position,
            Long totalWaiting,
            Long estimatedWaitSeconds,
            Long nextPollAfterSeconds
    ) {
        public static QueueEntryResult of(Long position, Long totalWaiting, Long estimatedWaitSeconds, Long nextPollAfterSeconds) {
            return new QueueEntryResult(position, totalWaiting, estimatedWaitSeconds, nextPollAfterSeconds);
        }
    }

    public record QueuePositionResult(
            Long position,
            Long totalWaiting,
            Long estimatedWaitSeconds,
            String token,
            Long nextPollAfterSeconds
    ) {
        public static QueuePositionResult of(Long position, Long totalWaiting, Long estimatedWaitSeconds, String token, Long nextPollAfterSeconds) {
            return new QueuePositionResult(position, totalWaiting, estimatedWaitSeconds, token, nextPollAfterSeconds);
        }
    }
}
