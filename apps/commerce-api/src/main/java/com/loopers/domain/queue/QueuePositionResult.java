package com.loopers.domain.queue;

public record QueuePositionResult(
        Status status,
        Long position,
        Long totalWaiting,
        Long estimatedWaitSeconds,
        String token
) {
    public enum Status {
        WAITING, TOKEN_ISSUED, NOT_IN_QUEUE
    }

    public static QueuePositionResult waiting(long position, long totalWaiting, long estimatedWaitSeconds) {
        return new QueuePositionResult(Status.WAITING, position, totalWaiting, estimatedWaitSeconds, null);
    }

    public static QueuePositionResult tokenIssued(String token) {
        return new QueuePositionResult(Status.TOKEN_ISSUED, null, null, null, token);
    }

    public static QueuePositionResult notInQueue() {
        return new QueuePositionResult(Status.NOT_IN_QUEUE, null, null, null, null);
    }
}
