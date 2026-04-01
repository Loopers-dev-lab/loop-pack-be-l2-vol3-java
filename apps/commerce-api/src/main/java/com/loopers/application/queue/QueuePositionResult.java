package com.loopers.application.queue;

public record QueuePositionResult(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        String token
) {

    public static QueuePositionResult admitted(String token) {
        return new QueuePositionResult(0, 0, 0, token);
    }
}
